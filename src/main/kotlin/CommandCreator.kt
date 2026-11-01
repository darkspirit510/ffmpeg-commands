import java.io.IOException
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
const val DEINTERLACE = "deinterlace"
const val TRIM_START = "trimStart"

private const val FILE_DOES_NOT_EXIST = "Error opening input files: No such file or directory"
// ffmpeg stops probing once all streams are resolved and buffers what it read until the packets are processed.
private const val EXTENDED_PROBE = "-analyzeduration 600M -probesize 5G "
private const val VIDEO_ENCODER = "libsvtav1"
private const val GOP_PROGRESSIVE = "-g 240 -keyint_min 240"
private const val GOP_DEINTERLACED = "-g 480 -keyint_min 480"
// 10-bit output keeps near-black gradients intact even for 8-bit sources; tune=0 favours visual quality over PSNR,
// variance boost spends extra bits on flat, low-contrast areas.
private const val ENCODER_OPTIONS = "-pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"
private const val QUALITY_OPTIONS = "-crf 17 -preset 2"
// Marks which settings encoded the video stream; TOOL_VERSION is generated from the version in build.gradle.kts.
private const val VERSION_TAG = "-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION"

class CommandCreator {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")
    private val unsafeShellCharacters = Regex("""[^\p{L}\p{N}_./,:@%+=-]""")
    private val positiveSeconds = Regex("""\d+(\.\d+)?""")
    private val unprobedSubtitle = Regex("""Could not find codec parameters for stream \d+ \(Subtitle:""")
    private val knownParameters = listOf(
        ALIAS,
        ADDITIONAL_LANGUAGES,
        DOCKER,
        DROP_SUBTITLES,
        IGNORE_MISSING_SUBTITLE_LANGUAGE,
        SET_AUDIO_LANGUAGES,
        UNSTARTED,
        MAX_INTERLEAVE_DELTA,
        TWO_PASS_TRANSCODE,
        DEINTERLACE,
        TRIM_START
    )

    private val ffmpegWrapper: FfmpegWrapper
    private val weightsInstaller: WeightsInstaller

    constructor() : this(FfmpegWrapperImpl())

    constructor(wrapper: FfmpegWrapper) : this(wrapper, WeightsInstallerImpl())

    constructor(
        wrapper: FfmpegWrapper,
        installer: WeightsInstaller
    ) {
        this.ffmpegWrapper = wrapper
        this.weightsInstaller = installer
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
        val deinterlace = parsedArgs.contains(DEINTERLACE)

        val ffmpegResult = try {
            ffmpegWrapper.read(args[0])
        } catch (e: IOException) {
            return "[Error] Could not run ffmpeg: ${e.message}"
        }

        if (ffmpegResult.any { it.contains(FILE_DOES_NOT_EXIST) }) {
            return "[Error] File ${args[0]} does not exist or can't be accessed."
        }

        val missingLanguageError = checkMissingLanguage(ffmpegResult, parsedArgs)

        if (missingLanguageError != null) {
            return missingLanguageError
        }

        val streams = ffmpegResult
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("Stream") }
            .filter { !it.startsWith("Guessed") }
            .map { Stream.from(it, parsedArgs) }
            .filterNotNull()
            .groupBy { it.type }
            .filter { knownChannelTypes.contains(it.key) }

        if (streams["Video"].isNullOrEmpty()) {
            return "[Error] No video stream found in ${args[0]}."
        }

        if (deinterlace) {
            try {
                weightsInstaller.install()
            } catch (e: IOException) {
                return "[Error] Could not install nnedi weights: ${e.message}"
            }
        }

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

        val weightsDir = if (useDocker) {
            "/weights"
        } else {
            "\$HOME/.cache/ffmpeg-commands"
        }

        val videoFormat = videoFormat(streams, deinterlace, weightsDir)
        val copyVideo = videoFormat == "copy"
        val trimStart = parsedArgs[TRIM_START]

        if (trimStart != null && copyVideo) {
            return "[Error] -trimStart cannot be used on AV1 sources, a copied video stream can only be cut at a keyframe"
        }

        val probe = if (ffmpegResult.any { unprobedSubtitle.containsMatchIn(it) }) {
            EXTENDED_PROBE
        } else {
            ""
        }

        // The seek lands on the keyframe before the cut, so copied streams need their earlier packets discarded.
        val seek = trimStart?.let { "-ss $it " } ?: ""
        val discardBeforeCut = trimStart?.let { "-copypriorss 0 " } ?: ""

        // After a seek, subtitles sharing an input with audio ended audio encodes right after the cut on real files.
        val separateSubtitles = trimStart != null && subtitleMappings(streams, takeLanguages, parsedArgs, 0).isNotEmpty()
        val subtitleInput = if (separateSubtitles) {
            "$probe$seek-i $inputFile "
        } else {
            ""
        }

        // A copied video stream needs no separate first pass, and no encoder consumes -crf/-preset.
        val useTwoPass = parsedArgs.contains(TWO_PASS_TRANSCODE) && !copyVideo

        if (useTwoPass) {
            val videoOutputName = outputName(filename.substringAfterLast("/"))
            val videoOutputFile = "$outputDir/${videoOutputName.substringBeforeLast(".")}_video.mkv"
            val finalOutputFile = "$outputDir/$videoOutputName"

            val videoCommand = (commandPrefix + "-n $probe$seek-i $inputFile " +
                "-map 0:v:0 -c:v:0 $videoFormat " +
                "$QUALITY_OPTIONS -f matroska " +
                "$videoOutputFile").replace("  ", " ").trim()

            val subtitleInputIndex = if (separateSubtitles) {
                2
            } else {
                0
            }

            val audioCommand = (commandPrefix + "-n $probe$seek-i $inputFile -i $videoOutputFile $subtitleInput" +
                "-map 1:v:0 -c:v:0 copy " +
                "${audioMappings(streams, takeLanguages, parsedArgs)} " +
                "${subtitleMappings(streams, takeLanguages, parsedArgs, subtitleInputIndex)} " +
                attachmentMapping(streams) +
                "$VERSION_TAG $discardBeforeCut-max_muxing_queue_size 9999 -max_interleave_delta $interleaveDelta " +
                "$finalOutputFile").replace("  ", " ").trim()

            val finalVideoCommand = if (useDocker) {
                dockerWrap(videoCommand, useUnstarted, deinterlace)
            } else {
                videoCommand
            }

            val finalAudioCommand = if (useDocker) {
                dockerWrap(audioCommand, useUnstarted, false)
            } else {
                audioCommand
            }

            return "$finalVideoCommand\n$finalAudioCommand"
        }

        val qualityOptions = if (copyVideo) {
            ""
        } else {
            "$QUALITY_OPTIONS $VERSION_TAG "
        }

        val subtitleInputIndex = if (separateSubtitles) {
            1
        } else {
            0
        }

        val baseCommand = (commandPrefix + "-n $probe$seek-i $inputFile $subtitleInput" +
            "-map 0:v:0 -c:v:0 $videoFormat " +
            "${audioMappings(streams, takeLanguages, parsedArgs)} " +
            "${subtitleMappings(streams, takeLanguages, parsedArgs, subtitleInputIndex)} " +
            attachmentMapping(streams) +
            qualityOptions +
            discardBeforeCut +
            "-max_muxing_queue_size 9999 -max_interleave_delta $interleaveDelta " +
            "$outputDir/${outputName(filename.substringAfterLast("/"))}")
            .replace("  ", " ")
            .trim()

        return if (useDocker) {
            dockerWrap(baseCommand, useUnstarted, deinterlace)
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
        validateTrimStart(parsed)

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

    private fun validateTrimStart(parsedArgs: Map<String, String>) {
        val value = parsedArgs[TRIM_START]

        if (value != null && (!positiveSeconds.matches(value) || value.toDouble() <= 0)) {
            throw IllegalArgumentException("trimStart must be a positive number of seconds")
        }
    }

    private fun checkMissingLanguage(lines: List<String>, parsedArgs: Map<String, String>): String? {
        val hasSetAudioLanguages = parsedArgs.contains(SET_AUDIO_LANGUAGES)
        val ignoreSubtitle = parsedArgs.contains(IGNORE_MISSING_SUBTITLE_LANGUAGE)

        for (line in lines) {
            val trimmed = line.trim()

            if (!trimmed.contains("Stream") || trimmed.startsWith("Guessed")) {
                continue
            }

            if (Stream.patternWithLang.matcher(trimmed).matches()) {
                continue
            }

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

    private fun escape(filename: String): String = unsafeShellCharacters.replace(filename) { "\\${it.value}" }

    private fun dockerWrap(command: String, unstarted: Boolean, deinterlace: Boolean): String {
        val volumePath = "\"\$(pwd)\""
        val dockerCommand = if (unstarted) "create" else "run"
        val weightsVolume = if (deinterlace) {
            "-v \"\$HOME/.cache/ffmpeg-commands\":/weights "
        } else {
            ""
        }

        return "docker $dockerCommand --rm -it -v $volumePath:/config ${weightsVolume}linuxserver/ffmpeg $command"
    }

    private fun languageList(parameters: Map<String, String>): List<String> = defaultLanguages.plus(
        parameters[ADDITIONAL_LANGUAGES]
            ?.split(",")
            ?: emptyList()
    )

    private fun videoFormat(streams: Map<String, List<Stream>>, deinterlace: Boolean, weightsDir: String): String =
        if (deinterlace) {
            "$VIDEO_ENCODER $GOP_DEINTERLACED $ENCODER_OPTIONS " +
                "-vf \"nnedi=weights=$weightsDir/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\""
        } else if (streams["Video"]!!.first().codec.startsWith("av1")) {
            "copy"
        } else {
            "$VIDEO_ENCODER $GOP_PROGRESSIVE $ENCODER_OPTIONS"
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

        val mappings = takeLanguages
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

        val audioPart = mappings
            .mapIndexed { idx, mapping -> "-map 0:a:${mapping.index} -c:a:$idx ${mapping.action}" }
            .joinToString(" ")

        val metadataPart = if (noLangAssignments.isNotEmpty()) {
            noLangAssignments.entries
                .map { (streamIdx, lang) -> mappings.indexOfFirst { it.index == streamIdx } to lang }
                .filter { (mappingIdx, _) -> mappingIdx >= 0 }
                .sortedBy { (mappingIdx, _) -> mappingIdx }
                .joinToString(" ") { (mappingIdx, lang) -> "-metadata:s:a:$mappingIdx language=$lang" }
        } else ""

        return listOfNotNull(audioPart, metadataPart)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun audioMappingsFor(sourceMappings: List<Pair<Int, Stream>>, setAudioLangs: List<String>): List<Mapping> {
        val audioMappings = mutableListOf<Mapping>()

        sourceMappings.forEach {
            audioMappings.add(
                Mapping(
                    index = it.first,
                    codec = it.second.codec,
                    action = "copy"
                )
            )
        }

        if (audioMappings.any { !it.codec.startsWith("ac3") } && setAudioLangs.isEmpty() && audioMappings.none {
                it.codec.startsWith("ac3")
                    && !it.codec.contains("stereo, fltp, 192 kb/s")
            }) {
            val lastNonAC3Index = audioMappings
                .indexOf(audioMappings.last { !it.codec.startsWith("ac3") })
            val transcodeSource = audioMappings.first { !it.codec.startsWith("ac3") }
            audioMappings.add(lastNonAC3Index + 1, transcodeSource.copy(action = "ac3"))
        }

        return audioMappings
    }

    private fun subtitleMappings(
        streams: Map<String, List<Stream>>,
        takeLanguages: List<String>,
        parsedArgs: Map<String, String>,
        inputIndex: Int
    ): String {
        if (parsedArgs.contains(DROP_SUBTITLES)) {
            return ""
        }

        val subtitleCommands = mutableListOf<String>()

        takeLanguages.forEach { lang ->
            streams["Subtitle"]?.forEachIndexed { idx, it ->
                if (it.lang == lang) {
                    subtitleCommands.add("-map $inputIndex:s:$idx -c:s:${subtitleCommands.size} copy ")
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
