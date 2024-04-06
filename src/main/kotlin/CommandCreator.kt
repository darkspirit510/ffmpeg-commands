import java.util.regex.Pattern

fun main(args: Array<String>) {
    println(CommandCreator().doAction(args))
}

class CommandCreator {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")

    private val ffmpegWrapper: FfmpegWrapper

    constructor() {
        this.ffmpegWrapper = FfmpegWrapperImpl()
    }

    constructor(wrapper: FfmpegWrapper) {
        this.ffmpegWrapper = wrapper
    }

    fun doAction(args: Array<String>): String {
        val takeLanguages = languageList(args).distinct()

        val streams = ffmpegWrapper
            .read(args[0])
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("Stream") }
            .filter { !it.startsWith("Guessed") }
            .map { Stream.from(it) }
            .groupBy { it.type }

        if (streams["Video"]!!.size > 1) {
            throw IllegalArgumentException("Multiple video streams found")
        }

        if (streams.keys.any { !knownChannelTypes.contains(it) }) {
            throw IllegalArgumentException("Unknown stream type found")
        }

        val filename = escape(args[0])

        return ("ffmpeg -n -i $filename " +
                "-map 0:v -c:v ${videoFormat(streams)} " +
                "${audioMappings(streams, takeLanguages)} " +
                "${subtitleMappings(streams, takeLanguages)} " +
                attachmentMapping(streams) +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 " +
                "Output/${outputName(filename)}")
            .replace("  ", " ")
    }

    private fun attachmentMapping(streams: Map<String, List<Stream>>) = if (streams.keys.contains("Attachment")) {
        "-map 0:t -c:t copy "
    } else {
        ""
    }

    private fun escape(filename: String): String {
        var escapedFilename = filename

        listOf(" ", "`", "(", ")").forEach {
            escapedFilename = escapedFilename.replace(it, "\\$it")
        }

        return escapedFilename
    }

    private fun languageList(args: Array<String>) = if (args.size > 1 && args[0].isNotEmpty()) {
        defaultLanguages.plus(args[1].split(","))
    } else {
        defaultLanguages
    }

    private fun videoFormat(streams: Map<String, List<Stream>>): String =
        if (streams["Video"]!!.single().codec.startsWith("hevc")) {
            "copy"
        } else {
            "libx265"
        }

    private fun audioMappings(streams: Map<String, List<Stream>>, takeLanguages: List<String>): String = takeLanguages
        .flatMap { lang ->
            audioMappingsFor(streams["Audio"]?.mapIndexedNotNull { idx, it ->
                if (it.lang == lang) {
                    Pair(idx, it)
                } else {
                    null
                }
            } ?: emptyList()
            )
        }
        .mapIndexed { idx, mapping -> "-map 0:a:${mapping.index} -c:a:$idx ${mapping.action}" }
        .joinToString(" ")

    private fun audioMappingsFor(sourceMappings: List<Pair<Int, Stream>>): List<Mapping> {
        val audioMappings = mutableListOf<Mapping>()

        sourceMappings.forEach {
            audioMappings.add(Mapping(it.first, it.second.codec, "copy"))
        }

        if (audioMappings.any { !it.codec.startsWith("ac3") }
            && audioMappings.none { it.codec.startsWith("ac3") && !it.codec.endsWith("stereo, fltp, 192 kb/s") }) {
            val lastNonAC3Index = audioMappings
                .indexOf(audioMappings.last { !it.codec.startsWith("ac3") })
            audioMappings.add(lastNonAC3Index + 1, audioMappings.first().copy(action = "ac3"))
        }

        return audioMappings
    }

    private fun subtitleMappings(streams: Map<String, List<Stream>>, takeLanguages: List<String>): String {
        val subtitleCommands = mutableListOf<String>()

        takeLanguages.forEach { lang ->
            streams["Subtitle"]?.forEachIndexed { idx, it ->
                if (it.lang == lang) {
                    subtitleCommands.add("-map 0:s:$idx -c:s:${subtitleCommands.size} copy ")
                }
            }
        }

        return subtitleCommands.joinToString("") { it }
    }

    private fun outputName(filename: String) = "${filename.substringBeforeLast(".")}.mkv"
}

data class Stream(
    val index: Int,
    val lang: String,
    val type: String,
    val codec: String
) {
    companion object {
        private val patternWithLang =
            Pattern.compile("""Stream #0:(?<index>\d+)\((?<lang>\w+)\): (?<type>\w+): (?<codec>.*)""")
        private val patternWithoutLang = Pattern.compile("""Stream #0:(?<index>\d+): (?<type>\w+): (?<codec>.*)""")

        fun from(raw: String): Stream {
            with(patternWithLang
                .matcher(raw)
                .apply {
                    if (!matches()) {
                        with(patternWithoutLang
                            .matcher(raw)
                            .apply {
                                if (!matches() || !setOf("Video", "Attachment").contains(group("type"))) {
                                    throw IllegalArgumentException("Missing language for stream")
                                }
                            }) {
                            return Stream(
                                index = group("index").toInt(),
                                lang = "???",
                                type = group("type"),
                                codec = group("codec")
                            )
                        }
                    }
                }
            ) {
                return Stream(
                    index = group("index").toInt(),
                    lang = group("lang"),
                    type = group("type"),
                    codec = group("codec")
                )
            }
        }
    }
}

data class Mapping(
    val index: Int,
    val codec: String,
    val action: String
)
