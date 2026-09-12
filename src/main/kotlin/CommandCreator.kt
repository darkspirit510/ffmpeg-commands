import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val result = CommandCreator().doAction(args)

    if (result.startsWith("[Error]")) {
        println(result)
        exitProcess(1)
    }

    println(result)
}

const val ADDITIONAL_LANGUAGES = "additionalLanguages"
const val DROP_SUBTITLES = "dropSubtitles"
const val IGNORE_MISSING_SUBTITLE_LANGUAGE = "ignoreMissingSubtitleLanguage"
const val SET_AUDIO_LANGUAGES = "setAudioLanguages"
const val ALIAS = "alias"
const val DOCKER = "docker"
const val UNSTARTED = "unstarted"
const val MAX_INTERLEAVE_DELTA = "maxInterleaveDelta"
const val TWO_PASS_TRANSCODE = "twoPassTranscode"

private const val FILE_DOES_NOT_EXIST = "Error opening input files: No such file or directory"

class CommandCreator {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")
    private val escapeCharacters = listOf(" ", "`", "(", ")", "!", "?")
    private val knownParameters = listOf(
        ALIAS,
        ADDITIONAL_LANGUAGES,
        DOCKER,
        DROP_SUBTITLES,
        IGNORE_MISSING_SUBTITLE_LANGUAGE,
        SET_AUDIO_LANGUAGES,
        UNSTARTED,
        MAX_INTERLEAVE_DELTA,
        TWO_PASS_TRANSCODE
    )

    private val ffmpegWrapper: FfmpegWrapper

    constructor() {
        this.ffmpegWrapper = FfmpegWrapperImpl()
    }

    constructor(wrapper: FfmpegWrapper) {
        this.ffmpegWrapper = wrapper
    }

    fun doAction(args: Array<String>): String {
        if (args.isEmpty()) {
            return "[Error] Missing parameter filename. Usage: java -jar ffmpeg-commands.jar filename.mkv [-additionalParameters]"
        }

        val parsedArgs = try {
            parseArgs(args)
        } catch (e: IllegalArgumentException) {
            return "[Error] ${e.message}"
        }

        if (parsedArgs.contains(ALIAS) && parsedArgs.contains(DOCKER)) {
            return "[Error] Cannot use alias and docker options together"
        }

        if (parsedArgs.contains(UNSTARTED) && !parsedArgs.contains(DOCKER)) {
            return "[Error] -unstarted parameter can only be used with -docker"
        }

        val takeLanguages = languageList(parsedArgs).distinct()

        val ffmpegResult = ffmpegWrapper.read(args[0])

        if (ffmpegResult.any { it.contains(FILE_DOES_NOT_EXIST) }) {
            return "[Error] File ${args[0]} does not exist or can't be accessed."
        }

        val missingLanguageError = checkMissingLanguage(ffmpegResult, parsedArgs)
        if (missingLanguageError != null) return missingLanguageError

        val streams = ffmpegResult
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("Stream") }
            .filter { !it.startsWith("Guessed") }
            .map { Stream.from(it, parsedArgs) }
            .filterNotNull()
            .groupBy { it.type }
            .filter { knownChannelTypes.contains(it.key) }

        val filename = escape(args[0])
        val useDocker = parsedArgs.contains(DOCKER)
        val useUnstarted = parsedArgs.contains(UNSTARTED)

        val inputFile = if (useDocker) {
            "/config/${filename.substringAfterLast("/")}"
        } else {
            filename
        }

        val outputDir = if (useDocker) {
            "/config/Output"
        } else {
            "Output"
        }

        val commandPrefix = if (useDocker) {
            ""
        } else {
            command(parsedArgs) + " "
        }

        val interleaveDelta = parsedArgs[MAX_INTERLEAVE_DELTA]?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.times(1000)
            ?: 0L

        val useTwoPass = parsedArgs.contains(TWO_PASS_TRANSCODE)

        if (useTwoPass) {
            val videoOutputName = outputName(filename.substringAfterLast("/"))
            val videoOutputFile = "$outputDir/${videoOutputName.substringBeforeLast(".")}_video.mkv"
            val finalOutputFile = "$outputDir/$videoOutputName"

            val videoCommand = (commandPrefix + "-n -i $inputFile " +
                "-map 0:v:0 -c:v:0 ${videoFormat(streams)} " +
                "-crf 17 -preset 2 -f matroska " +
                "$videoOutputFile").replace("  ", " ").trim()

            val audioCommand = (commandPrefix + "-n -i $inputFile -i $videoOutputFile " +
                "-map 1:v:0 -c:v:0 copy " +
                "${audioMappings(streams, takeLanguages, parsedArgs)} " +
                "${subtitleMappings(streams, takeLanguages, parsedArgs)} " +
                attachmentMapping(streams) +
                "-max_muxing_queue_size 9999 -max_interleave_delta $interleaveDelta " +
                "$finalOutputFile").replace("  ", " ").trim()

            val finalVideoCommand = if (useDocker) {
                val volumePath = "\"\$(pwd)\""
                "docker run --rm -it -v $volumePath:/config linuxserver/ffmpeg $videoCommand"
            } else {
                videoCommand
            }

            val finalAudioCommand = if (useDocker) {
                val volumePath = "\"\$(pwd)\""
                "docker run --rm -it -v $volumePath:/config linuxserver/ffmpeg $audioCommand"
            } else {
                audioCommand
            }

            return "$finalVideoCommand\n$finalAudioCommand"
        }

        val baseCommand = (commandPrefix + "-n -i $inputFile " +
            "-map 0:v:0 -c:v:0 ${videoFormat(streams)} " +
            "${audioMappings(streams, takeLanguages, parsedArgs)} " +
            "${subtitleMappings(streams, takeLanguages, parsedArgs)} " +
            attachmentMapping(streams) +
            "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta $interleaveDelta " +
            "$outputDir/${outputName(filename.substringAfterLast("/"))}")
            .replace("  ", " ")
            .trim()

        return if (useDocker) {
            val volumePath = "\"\$(pwd)\""

            if (useUnstarted) {
                "docker create --rm -it -v $volumePath:/config linuxserver/ffmpeg $baseCommand"
            } else {
                "docker run --rm -it -v $volumePath:/config linuxserver/ffmpeg $baseCommand"
            }
        } else {
            baseCommand
        }
    }

    private fun command(parsedArgs: Map<String, String>): String = parsedArgs[ALIAS] ?: "ffmpeg"

    private fun parseArgs(args: Array<String>): Map<String, String> {
        args.drop(1)
            .map { it.drop(1).substringBefore("=") }
            .forEach {
                if (!knownParameters.contains(it)) {
                    throw IllegalArgumentException("Unknown option $it")
                }
            }

        val parsed = knownParameters
            .associateWith { args.option(it) }
            .filter { it.value != null } as Map<String, String>

        validateMaxInterleaveDelta(parsed)

        return parsed
    }

    private fun validateMaxInterleaveDelta(parsedArgs: Map<String, String>) {
        val value = parsedArgs[MAX_INTERLEAVE_DELTA]

        if (value != null) {
            if (value.isEmpty()) {
                throw IllegalArgumentException("maxInterleaveDelta cannot be empty")
            }

            try {
                val num = value.toLong()

                if (num < 0) {
                    throw IllegalArgumentException("maxInterleaveDelta must be non-negative")
                }
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("maxInterleaveDelta must be an integer")
            }
        }
    }

    private fun checkMissingLanguage(lines: List<String>, parsedArgs: Map<String, String>): String? {
        val hasSetAudioLanguages = parsedArgs.contains(SET_AUDIO_LANGUAGES)
        val ignoreSubtitle = parsedArgs.contains(IGNORE_MISSING_SUBTITLE_LANGUAGE)

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.contains("Stream") || trimmed.startsWith("Guessed")) continue
            if (Stream.patternWithLang.matcher(trimmed).matches()) continue

            val matcher = Stream.patternWithoutLang.matcher(trimmed)
            if (matcher.matches()) {
                when (matcher.group("type")) {
                    "Audio" -> if (!hasSetAudioLanguages) {
                        return "[Error] Missing language for audio stream. Use -setAudioLanguages parameter to specify languages for audio streams without language tags."
                    }

                    "Subtitle" -> if (!ignoreSubtitle) {
                        return "[Error] Missing language for subtitle stream"
                    }
                }
            }
        }
        return null
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
        if (streams["Video"]!!.first().codec.startsWith("av1")) {
            "copy"
        } else {
            "libsvtav1 -g 240 -keyint_min 240"
        }

    private fun audioMappings(
        streams: Map<String, List<Stream>>,
        takeLanguages: List<String>,
        parsedArgs: Map<String, String>
    ): String {
        val audioStreams = streams["Audio"] ?: emptyList()
        val setAudioLangs = parsedArgs[SET_AUDIO_LANGUAGES]?.split(",") ?: emptyList()

        val noLangAssignments = mutableMapOf<Int, String>()

        if (setAudioLangs.isNotEmpty()) {
            var langIdx = 0
            audioStreams.forEachIndexed { idx, stream ->
                if (stream.lang == "???" && langIdx < setAudioLangs.size) {
                    noLangAssignments[idx] = setAudioLangs[langIdx]
                    langIdx++
                }
            }
        }

        val baseMappings = takeLanguages
            .flatMap { lang ->
                audioMappingsFor(audioStreams.mapIndexedNotNull { idx, stream ->
                    if (stream.lang == lang ||
                        (stream.lang == "???" && noLangAssignments[idx] == lang)
                    ) {
                        Pair(idx, stream)
                    } else {
                        null
                    }
                }, setAudioLangs)
            }
            .distinct()
            .toList()

        val mappings = if (setAudioLangs.isNotEmpty()) {
            baseMappings.sortedBy { it.index }
        } else {
            baseMappings
        }

        val audioPart = mappings
            .mapIndexed { idx, mapping -> "-map 0:a:${mapping.index} -c:a:$idx ${mapping.action}" }
            .joinToString(" ")

        val metadataPart = if (noLangAssignments.isNotEmpty()) {
            noLangAssignments.entries
                .sortedBy { it.key }
                .mapNotNull { (streamIdx, lang) ->
                    val mappingIdx = mappings.indexOfFirst { it.index == streamIdx }
                    if (mappingIdx >= 0) {
                        "-metadata:s:a:$mappingIdx language=$lang"
                    } else {
                        null
                    }
                }
                .joinToString(" ")
        } else ""

        return listOfNotNull(audioPart, metadataPart)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun audioMappingsFor(sourceMappings: List<Pair<Int, Stream>>, setAudioLangs: List<String>): List<Mapping> {
        val audioMappings = mutableListOf<Mapping>()

        sourceMappings.forEach {
            audioMappings.add(Mapping(it.first, it.second.codec, "copy"))
        }

        if (audioMappings.any { !it.codec.startsWith("ac3") } && setAudioLangs.isEmpty() && audioMappings.none {
                it.codec.startsWith(
                    "ac3"
                ) && !it.codec.endsWith("stereo, fltp, 192 kb/s")
            }) {
            val lastNonAC3Index = audioMappings
                .indexOf(audioMappings.last { !it.codec.startsWith("ac3") })
            audioMappings.add(lastNonAC3Index + 1, audioMappings.first().copy(action = "ac3"))
        }

        return audioMappings
    }

    private fun subtitleMappings(
        streams: Map<String, List<Stream>>,
        takeLanguages: List<String>,
        parsedArgs: Map<String, String>
    ): String {
        if (parsedArgs.contains(DROP_SUBTITLES)) {
            return ""
        }

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
