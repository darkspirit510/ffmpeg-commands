import java.util.regex.Pattern

fun main(args: Array<String>) {
    println(CommandCreator().doAction(args))
}

private const val ADDITIONAL_LANGUAGES = "additionalLanguages"
private const val IGNORE_MISSING_AUDIO_LANGUAGE = "ignoreMissingAudioLanguage"
private const val IGNORE_MISSING_SUBTITLE_LANGUAGE = "ignoreMissingSubtitleLanguage"
private const val PRESERVE_MISSING_AUDIO_LANGUAGE = "preserveMissingAudioLanguage"

class CommandCreator {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")
    private val escapeCharacters = listOf(" ", "`", "(", ")", "!", "?")
    private val knownParameters = listOf(
        ADDITIONAL_LANGUAGES,
        IGNORE_MISSING_AUDIO_LANGUAGE,
        IGNORE_MISSING_SUBTITLE_LANGUAGE,
        PRESERVE_MISSING_AUDIO_LANGUAGE
    )

    private val ffmpegWrapper: FfmpegWrapper

    constructor() {
        this.ffmpegWrapper = FfmpegWrapperImpl()
    }

    constructor(wrapper: FfmpegWrapper) {
        this.ffmpegWrapper = wrapper
    }

    fun doAction(args: Array<String>): String {
        val parsedArgs = parseArgs(args)

        val takeLanguages = languageList(parsedArgs).distinct()

        val streams = ffmpegWrapper
            .read(args[0])
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("Stream") }
            .filter { !it.startsWith("Guessed") }
            .map { Stream.from(it, parsedArgs) }
            .filterNotNull()
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
            "${audioMappings(streams, takeLanguages, parsedArgs)} " +
            "${subtitleMappings(streams, takeLanguages)} " +
            attachmentMapping(streams) +
            "-crf 17 -preset medium -max_muxing_queue_size 9999 " +
            "Output/${outputName(filename)}")
            .replace("  ", " ")
    }

    private fun parseArgs(args: Array<String>): Map<String, String> {
        args.drop(1)
            .map { it.drop(1).substringBefore("=") }
            .forEach {
                if (!knownParameters.contains(it)) {
                    throw IllegalArgumentException("Unknown option $it")
                }
            }

        return knownParameters
            .associateWith { args.option(it) }
            .filter { it.value != null } as Map<String, String>
    }

    private fun attachmentMapping(streams: Map<String, List<Stream>>) = if (streams.keys.contains("Attachment")) {
        "-map 0:t -c:t copy "
    } else {
        ""
    }

    private fun escape(filename: String): String {
        var escapedFilename = filename

        escapeCharacters.forEach {
            escapedFilename = escapedFilename.replace(it, "\\$it")
        }

        return escapedFilename
    }

    private fun languageList(parameters: Map<String, String>): List<String> = defaultLanguages.plus(
        parameters[ADDITIONAL_LANGUAGES]?.split(",")
            ?: emptyList()
    )

    private fun videoFormat(streams: Map<String, List<Stream>>): String =
        if (streams["Video"]!!.single().codec.startsWith("hevc")) {
            "copy"
        } else {
            "libx265"
        }

    private fun audioMappings(
        streams: Map<String, List<Stream>>,
        takeLanguages: List<String>,
        parsedArgs: Map<String, String>
    ): String = takeLanguages
        .flatMap { lang ->
            audioMappingsFor(streams["Audio"]?.mapIndexedNotNull { idx, it ->
                if (it.lang == lang || it.lang == "???" && parsedArgs.contains(PRESERVE_MISSING_AUDIO_LANGUAGE)) {
                    Pair(idx, it)
                } else {
                    null
                }
            } ?: emptyList()
            )
        }
        .toSet()
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

private fun Array<String>.option(parameter: String): String? {
    val split = firstOrNull { it.startsWith("-$parameter") }
        ?.split("=")

    return split?.let {
        return if (it.size == 1) {
            ""
        } else {
            it[1]
        }
    }
}

data class Stream(
    val index: Int,
    val lang: String,
    val type: String,
    val codec: String
) {
    companion object {
        private val patternWithLang = Pattern
            .compile("""Stream #0:(?<index>\d+)\((?<lang>\w+)\): (?<type>\w+): (?<codec>.*)""")
        private val patternWithoutLang = Pattern.compile("""Stream #0:(?<index>\d+): (?<type>\w+): (?<codec>.*)""")

        fun from(raw: String, parsedArgs: Map<String, String>): Stream? {
            with(
                patternWithLang
                    .matcher(raw)
                    .apply {
                        if (!matches()) {
                            with(
                                patternWithoutLang
                                    .matcher(raw)
                                    .apply {
                                        if (!matches() || !setOf("Video", "Attachment").contains(group("type"))) {
                                            if (!ignoreMissingLanguage(group("type"), parsedArgs)) {
                                                throw IllegalArgumentException("Missing language for stream ($raw)")
                                            }
                                        }
                                    }
                            ) {
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

        private fun ignoreMissingLanguage(type: String?, parsedArgs: Map<String, String>) = when (type) {
            "Audio" -> parsedArgs.contains(IGNORE_MISSING_AUDIO_LANGUAGE) || parsedArgs.contains(PRESERVE_MISSING_AUDIO_LANGUAGE)
            "Subtitle" -> parsedArgs.contains(IGNORE_MISSING_SUBTITLE_LANGUAGE)

            else -> true
        }
    }
}

data class Mapping(
    val index: Int,
    val codec: String,
    val action: String
)
