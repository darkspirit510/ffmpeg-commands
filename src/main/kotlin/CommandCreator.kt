import java.util.regex.Pattern

fun main(args: Array<String>) {
    println(CommandCreator().doAction(args))
}

class CommandCreator {

    private val ffmpegWrapper: FfmpegWrapper

    constructor() {
        this.ffmpegWrapper = FfmpegWrapperImpl()
    }

    constructor(wrapper: FfmpegWrapper) {
        this.ffmpegWrapper = wrapper
    }

    fun doAction(args: Array<String>): String {
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

        if (streams.keys.any { it != "Video" && it != "Audio" && it != "Subtitle" }) {
            throw IllegalArgumentException("Unknown stream type found")
        }

        val filename = args[0].replace(" ", "\\ ")

        return ("ffmpeg -n -i $filename " +
                "-map 0:v -c:v libx265 " +
                "${audioMappings(streams)} " +
                "${subtitleMappings(streams)} " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 " +
                "Output/${outputName(filename)}")
            .replace("  ", " ")
    }

    private fun audioMappings(streams: Map<String, List<Stream>>): String {
        val englishAudio = mutableListOf<Pair<Int, Stream>>()

        streams["Audio"]?.forEachIndexed { idx, it ->
            if (it.lang == "eng") {
                englishAudio.add(Pair(idx, it))
            }
        }

        val germanAudio = mutableListOf<Pair<Int, Stream>>()

        streams["Audio"]?.forEachIndexed { idx, it ->
            if (it.lang == "deu" || it.lang == "ger") {
                germanAudio.add(Pair(idx, it))
            }
        }

        return (mappingsFor(germanAudio) + mappingsFor(englishAudio))
            .mapIndexed { idx, mapping -> "-map 0:a:${mapping.index} -c:a:$idx ${mapping.action}" }
            .joinToString(" ")
    }

    private fun mappingsFor(sourceMappings: MutableList<Pair<Int, Stream>>): MutableList<Mapping> {
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

    private fun subtitleMappings(streams: Map<String, List<Stream>>): String {
        val germanSubtitles = mutableListOf<Int>()

        streams["Subtitle"]?.forEachIndexed { idx, it ->
            if (it.lang == "deu" || it.lang == "ger") {
                germanSubtitles.add(idx)
            }
        }

        val englishSubtitles = mutableListOf<Int>()

        streams["Subtitle"]?.forEachIndexed { idx, it ->
            if (it.lang == "eng") {
                englishSubtitles.add(idx)
            }
        }

        var subtitleCommand = ""

        (germanSubtitles + englishSubtitles).forEach {
            subtitleCommand += "-map 0:s:${it} "
        }

        return if (subtitleCommand.isNotBlank()) {
            "$subtitleCommand -c:s copy"
        } else {
            ""
        }

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
        private val pattern = Pattern.compile("""Stream #0:(?<index>\d+)\((?<lang>\w+)\): (?<type>\w+): (?<codec>.*)""")

        fun from(raw: String): Stream {
            with(pattern
                .matcher(raw)
                .apply {
                    if (!matches()) {
                        throw IllegalArgumentException("Missing language for stream")
                    }
                }
            ) {
                return Stream(
                    index = group("index").toInt(),
                    lang = group("lang"),
                    type = group("type"),
                    codec = group("codec"),
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
