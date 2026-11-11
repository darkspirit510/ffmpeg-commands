import java.io.IOException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val result = CommandCreator().doAction(args)

    if (result.startsWith(ERROR_PREFIX)) {
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
const val REDUCE_RAM = "reduceRAM"
const val TWO_PASS_THRESHOLD = "twoPassThreshold"

private const val FILE_DOES_NOT_EXIST = "Error opening input files: No such file or directory"
// ffmpeg stops probing once all streams are resolved and buffers what it read until the packets are processed.
private const val EXTENDED_PROBE = "-analyzeduration 600M -probesize 5G "
private const val VIDEO_ENCODER = "libsvtav1"
private const val GOP_PROGRESSIVE = "-g 240 -keyint_min 240"
private const val GOP_DEINTERLACED = "-g 480 -keyint_min 480"
// 10-bit output keeps near-black gradients intact even for 8-bit sources; tune=0 favours visual quality over PSNR,
// variance boost spends extra bits on flat, low-contrast areas.
private const val ENCODER_OPTIONS = "-pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"
// SVT-AV1 levels above 3 buffer extra mini-GOPs in CRF mode, the auto level counts the host CPUs and goes up to 6.
// The level only changes speed, CRF output stays the same; decoder threads hold frames in flight as well.
private const val LOW_PARALLELISM = ":lp=3"
private const val DECODER_THREADS = "-threads 4 "
private const val QUALITY_OPTIONS = "-crf 17 -preset 2"
// Marks which settings encoded the video stream; TOOL_VERSION is generated from the version in build.gradle.kts.
private const val VERSION_TAG = "-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION"
const val COPY = "copy"
const val AC3 = "ac3"
private const val SOURCE_VIDEO_MAPPING = "-map 0:v:0 -c:v:0"
private const val ATTACHMENT_MAPPING = "-map 0:t -c:t $COPY"
private const val MUXING_QUEUE_OPTION = "-max_muxing_queue_size 9999"
// The intermediate file of -twoPassTranscode carries no chapters or global tags; mkvmerge takes them from the source.
// A bare -map_metadata -1 would also drop the stream tags, i.e. the language and title of the AC3 encodes.
private const val NO_CHAPTERS_OR_GLOBAL_METADATA = "-map_chapters -1 -map_metadata:g -1"
private const val MATROSKA_INPUT = "Input #0, matroska"
// The server container has 31 GB; SVT-AV1 and the decoder need room next to the muxer.
private const val DEFAULT_TWO_PASS_THRESHOLD_GB = "8"
private const val NNEDI_OPTIONS = "field=af:deint=interlaced:nns=n128:qual=slow"

private const val LOCAL_OUTPUT_DIR = "Output"
private const val LOCAL_WEIGHTS_DIR = "\$HOME/.cache/ffmpeg-commands"
private const val DOCKER_IMAGE = "linuxserver/ffmpeg"
private const val MKVMERGE = "mkvmerge"
private const val MKVMERGE_DOCKER_IMAGE = "jlesage/mkvtoolnix"
private const val DOCKER_CONFIG_DIR = "/config"
private const val DOCKER_OUTPUT_DIR = "$DOCKER_CONFIG_DIR/$LOCAL_OUTPUT_DIR"
private const val DOCKER_WEIGHTS_DIR = "/weights"
private const val DOCKER_CONFIG_VOLUME = "\"\$(pwd)\":$DOCKER_CONFIG_DIR"
private const val DOCKER_WEIGHTS_VOLUME = "\"$LOCAL_WEIGHTS_DIR\":$DOCKER_WEIGHTS_DIR"

private const val ERROR_PREFIX = "[Error]"
private const val MISSING_FILENAME =
    "$ERROR_PREFIX Missing parameter filename. Usage: java -jar ffmpeg-commands.jar filename.mkv [-additionalParameters]"
private const val ALIAS_WITH_DOCKER = "$ERROR_PREFIX Cannot use alias and docker options together"
private const val UNSTARTED_WITHOUT_DOCKER = "$ERROR_PREFIX -unstarted parameter can only be used with -docker"
private const val COULD_NOT_RUN_FFMPEG = "$ERROR_PREFIX Could not run ffmpeg:"
private const val COULD_NOT_INSTALL_WEIGHTS = "$ERROR_PREFIX Could not install nnedi weights:"
private const val TRIM_START_ON_COPIED_VIDEO =
    "$ERROR_PREFIX -trimStart cannot be used on AV1 sources, a copied video stream can only be cut at a keyframe"
private const val REDUCE_RAM_ON_COPIED_VIDEO =
    "$ERROR_PREFIX -reduceRAM cannot be used on AV1 sources, the copied video stream is not encoded"
private const val TRIM_START_WITH_TWO_PASS = "$ERROR_PREFIX -trimStart cannot be used with -twoPassTranscode yet"
private const val TWO_PASS_WITHOUT_MATROSKA = "$ERROR_PREFIX -twoPassTranscode only supports Matroska sources"
private const val MISSING_AUDIO_LANGUAGE =
    "$ERROR_PREFIX Missing language for audio stream. Use -setAudioLanguages parameter to specify languages for audio streams without language tags."
private const val MISSING_SUBTITLE_LANGUAGE = "$ERROR_PREFIX Missing language for subtitle stream"
private const val UNKNOWN_OPTION = "Unknown option"
private const val INTERLEAVE_DELTA_EMPTY = "maxInterleaveDelta cannot be empty"
private const val INTERLEAVE_DELTA_NEGATIVE = "maxInterleaveDelta must be non-negative"
private const val INTERLEAVE_DELTA_NOT_INTEGER = "maxInterleaveDelta must be an integer"
private const val TRIM_START_NOT_POSITIVE = "trimStart must be a positive number of seconds"
private const val TWO_PASS_THRESHOLD_NOT_POSITIVE = "twoPassThreshold must be a positive number of GB"
private const val SINGLE_PASS_NOT_CHECKED = "# Single pass, subtitle gaps not checked:"
private const val TWO_PASSES_DETECTED = "# Two passes:"
private const val TRIM_START_WITH_DETECTED_TWO_PASS = "$ERROR_PREFIX -trimStart cannot be used with two passes yet, but"
private const val TRIM_START_WITH_DETECTED_TWO_PASS_HINT = "Use -dropSubtitles, see issue #27"

class CommandCreator {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")
    private val unsafeShellCharacters = Regex("""[^\p{L}\p{N}_./,:@%+=-]""")
    private val positiveNumber = Regex("""\d+(\.\d+)?""")
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
        TRIM_START,
        REDUCE_RAM,
        TWO_PASS_THRESHOLD
    )

    private val ffmpegTracks = FfmpegTrackArguments()
    private val mkvmergeArguments = MkvmergeArguments()
    private val ffmpegWrapper: FfmpegWrapper
    private val weightsInstaller: WeightsInstaller
    private val subtitleGapCheck: SubtitleGapCheck

    constructor() : this(FfmpegWrapperImpl())

    constructor(wrapper: FfmpegWrapper) : this(wrapper, WeightsInstallerImpl())

    constructor(
        wrapper: FfmpegWrapper,
        installer: WeightsInstaller
    ) : this(wrapper, installer, EbmlCueReader())

    constructor(
        wrapper: FfmpegWrapper,
        cueReader: CueReader
    ) : this(wrapper, WeightsInstallerImpl(), cueReader)

    constructor(
        wrapper: FfmpegWrapper,
        installer: WeightsInstaller,
        cueReader: CueReader
    ) {
        this.ffmpegWrapper = wrapper
        this.weightsInstaller = installer
        this.subtitleGapCheck = SubtitleGapCheck(cueReader)
    }

    fun doAction(args: Array<String>): String {
        if (args.isEmpty()) {
            return MISSING_FILENAME
        }

        val parsedArgs = try {
            parseArgs(args)
        } catch (e: IllegalArgumentException) {
            return "$ERROR_PREFIX ${e.message}"
        }

        if (parsedArgs.contains(ALIAS) && parsedArgs.contains(DOCKER)) {
            return ALIAS_WITH_DOCKER
        }

        if (parsedArgs.contains(UNSTARTED) && !parsedArgs.contains(DOCKER)) {
            return UNSTARTED_WITHOUT_DOCKER
        }

        val takeLanguages = languageList(parsedArgs).distinct()
        val deinterlace = parsedArgs.contains(DEINTERLACE)

        val ffmpegResult = try {
            ffmpegWrapper.read(args[0])
        } catch (e: IOException) {
            return "$COULD_NOT_RUN_FFMPEG ${e.message}"
        }

        if (ffmpegResult.any { it.contains(FILE_DOES_NOT_EXIST) }) {
            return "$ERROR_PREFIX File ${args[0]} does not exist or can't be accessed."
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
            return "$ERROR_PREFIX No video stream found in ${args[0]}."
        }

        if (deinterlace) {
            try {
                weightsInstaller.install()
            } catch (e: IOException) {
                return "$COULD_NOT_INSTALL_WEIGHTS ${e.message}"
            }
        }

        val filename = escape(args[0])
        val useDocker = parsedArgs.contains(DOCKER)
        val useUnstarted = parsedArgs.contains(UNSTARTED)

        val inputFile = if (useDocker) {
            "$DOCKER_CONFIG_DIR/${filename.substringAfterLast("/")}"
        } else {
            filename
        }

        val outputDir = if (useDocker) {
            DOCKER_OUTPUT_DIR
        } else {
            LOCAL_OUTPUT_DIR
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
            DOCKER_WEIGHTS_DIR
        } else {
            LOCAL_WEIGHTS_DIR
        }

        val reduceRam = parsedArgs.contains(REDUCE_RAM)
        val videoFormat = videoFormat(streams, deinterlace, weightsDir, reduceRam)
        val selection = TrackSelection(streams, takeLanguages, parsedArgs)
        val copyVideo = videoFormat == COPY
        val trimStart = parsedArgs[TRIM_START]

        if (trimStart != null && copyVideo) {
            return TRIM_START_ON_COPIED_VIDEO
        }

        if (reduceRam && copyVideo) {
            return REDUCE_RAM_ON_COPIED_VIDEO
        }

        val decoderThreads = if (reduceRam) {
            DECODER_THREADS
        } else {
            ""
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
        val separateSubtitles = trimStart != null && selection.subtitles().isNotEmpty()
        val subtitleInput = if (separateSubtitles) {
            "$probe$seek-i $inputFile "
        } else {
            ""
        }

        val audio = try {
            selection.audio()
        } catch (e: IllegalArgumentException) {
            return "$ERROR_PREFIX ${e.message}"
        }

        // mkvmerge addresses source tracks by ffmpeg's stream index, which only holds for Matroska sources.
        val matroska = ffmpegResult.any { it.trim().startsWith(MATROSKA_INPUT) }
        val outputName = outputName(filename.substringAfterLast("/"))
        val videoStream = streams.getValue("Video").first().index

        // With nothing to encode, mkvmerge alone remuxes the source; it copes with sparse subtitle tracks.
        if (copyVideo && matroska && audio.all { it.action == COPY }) {
            return muxCommand(
                mkvmergeArguments.render(audio, selection.subtitles(), null, videoStream, inputFile, "$outputDir/$outputName"),
                outputName,
                useDocker,
                useUnstarted
            )
        }

        val notes = mutableListOf<String>()
        val useTwoPass = if (parsedArgs.contains(TWO_PASS_TRANSCODE)) {
            // Without Matroska, a copied video keeps the single ffmpeg command; an encoded one is rejected below.
            !copyVideo || matroska
        } else {
            val threshold = parsedArgs[TWO_PASS_THRESHOLD] ?: DEFAULT_TWO_PASS_THRESHOLD_GB

            when (val outcome = subtitleGapCheck.check(
                args[0],
                ffmpegResult,
                matroska,
                trimStart?.toDouble() ?: 0.0,
                videoStream,
                audio,
                selection.subtitles()
            )) {
                is SubtitleGapCheck.Outcome.NothingToCheck -> false

                is SubtitleGapCheck.Outcome.NotChecked -> {
                    notes.add("$SINGLE_PASS_NOT_CHECKED ${outcome.reason}")
                    false
                }

                is SubtitleGapCheck.Outcome.Estimated -> if (outcome.bytes > threshold.toDouble() * BYTES_PER_GB) {
                    if (trimStart != null) {
                        return "$TRIM_START_WITH_DETECTED_TWO_PASS ${outcome.describe(threshold)}. " +
                            TRIM_START_WITH_DETECTED_TWO_PASS_HINT
                    }

                    notes.add("$TWO_PASSES_DETECTED ${outcome.describe(threshold)}")
                    true
                } else {
                    false
                }
            }
        }

        if (useTwoPass) {
            if (trimStart != null) {
                return TRIM_START_WITH_TWO_PASS
            }

            if (!matroska) {
                return TWO_PASS_WITHOUT_MATROSKA
            }

            // A copied video goes straight from the source into mkvmerge, so the ffmpeg pass encodes only AC3.
            val inputPart = if (copyVideo) {
                commandPrefix + "-n $probe-i $inputFile "
            } else {
                commandPrefix + "-n $probe$decoderThreads-i $inputFile $SOURCE_VIDEO_MAPPING $videoFormat "
            }

            val videoOptions = if (copyVideo) {
                ""
            } else {
                "$QUALITY_OPTIONS $VERSION_TAG "
            }

            val sourceVideo = if (copyVideo) {
                videoStream
            } else {
                null
            }

            val commands = twoPassCommands(
                inputPart,
                videoOptions,
                sourceVideo,
                audio,
                selection.subtitles(),
                inputFile,
                outputDir,
                outputName,
                interleaveDelta,
                useDocker,
                useUnstarted,
                deinterlace
            )

            return (notes + commands).joinToString("\n")
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

        val baseCommand = (commandPrefix + "-n $probe$decoderThreads$seek-i $inputFile $subtitleInput" +
            "$SOURCE_VIDEO_MAPPING $videoFormat " +
            "${ffmpegTracks.audio(audio)} " +
            "${ffmpegTracks.subtitles(selection.subtitles(), subtitleInputIndex)} " +
            attachmentMapping(streams) +
            qualityOptions +
            discardBeforeCut +
            "$MUXING_QUEUE_OPTION -max_interleave_delta $interleaveDelta " +
            "$outputDir/$outputName")
            .replace("  ", " ")
            .trim()

        val command = if (useDocker) {
            dockerWrap(baseCommand, useUnstarted, deinterlace)
        } else {
            baseCommand
        }

        return (notes + command).joinToString("\n")
    }

    // Pass 1 encodes the video (unless mkvmerge copies sourceVideo) and the AC3 tracks, mkvmerge then adds everything
    // copied from the source. An ffmpeg mux of the source stalls on sparse subtitle tracks: it holds back the video
    // until the next subtitle line.
    private fun twoPassCommands(
        inputPart: String,
        videoOptions: String,
        sourceVideo: Int?,
        audio: List<Mapping>,
        subtitles: List<Mapping>,
        inputFile: String,
        outputDir: String,
        outputName: String,
        interleaveDelta: Long,
        useDocker: Boolean,
        useUnstarted: Boolean,
        deinterlace: Boolean
    ): String {
        val intermediateFile = "$outputDir/${outputName.substringBeforeLast(".")}_intermediate.mkv"
        val encodedAudio = audio.filter { it.action != COPY }

        val encodeCommand = (inputPart +
            "${ffmpegTracks.audio(encodedAudio)} " +
            "$videoOptions$NO_CHAPTERS_OR_GLOBAL_METADATA " +
            "$MUXING_QUEUE_OPTION -max_interleave_delta $interleaveDelta " +
            intermediateFile).replace("  ", " ").trim()

        val mkvmergeOptions = mkvmergeArguments.render(
            audio,
            subtitles,
            intermediateFile,
            sourceVideo,
            inputFile,
            "$outputDir/$outputName"
        )

        val finalEncodeCommand = if (useDocker) {
            dockerWrap(encodeCommand, useUnstarted, deinterlace)
        } else {
            encodeCommand
        }

        return "$finalEncodeCommand\n${muxCommand(mkvmergeOptions, outputName, useDocker, useUnstarted)}"
    }

    private fun muxCommand(mkvmergeOptions: String, outputName: String, useDocker: Boolean, useUnstarted: Boolean): String {
        val muxCommand = if (useDocker) {
            mkvmergeDockerWrap(mkvmergeOptions, useUnstarted)
        } else {
            "$MKVMERGE $mkvmergeOptions"
        }

        // mkvmerge has no -n and overwrites silently; for an unstarted container the check would run too early.
        return if (useUnstarted) {
            muxCommand
        } else {
            "[ ! -e $LOCAL_OUTPUT_DIR/$outputName ] && $muxCommand"
        }
    }

    private fun command(parsedArgs: Map<String, String>): String = parsedArgs[ALIAS] ?: "ffmpeg"

    private fun parseArgs(args: Array<String>): Map<String, String> {
        args.drop(1)
            .map { it.drop(1).substringBefore("=") }
            .forEach {
                if (!knownParameters.contains(it)) {
                    throw IllegalArgumentException("$UNKNOWN_OPTION $it")
                }
            }

        val parsed = knownParameters
            .associateWith { args.option(it) }
            .filter { it.value != null } as Map<String, String>

        validateMaxInterleaveDelta(parsed)
        validatePositiveNumber(parsed, TRIM_START, TRIM_START_NOT_POSITIVE)
        validatePositiveNumber(parsed, TWO_PASS_THRESHOLD, TWO_PASS_THRESHOLD_NOT_POSITIVE)

        return parsed
    }

    private fun validateMaxInterleaveDelta(parsedArgs: Map<String, String>) {
        val value = parsedArgs[MAX_INTERLEAVE_DELTA]

        if (value != null) {
            if (value.isEmpty()) {
                throw IllegalArgumentException(INTERLEAVE_DELTA_EMPTY)
            }

            try {
                val num = value.toLong()

                if (num < 0) {
                    throw IllegalArgumentException(INTERLEAVE_DELTA_NEGATIVE)
                }
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException(INTERLEAVE_DELTA_NOT_INTEGER)
            }
        }
    }

    private fun validatePositiveNumber(parsedArgs: Map<String, String>, parameter: String, message: String) {
        val value = parsedArgs[parameter]

        if (value != null && (!positiveNumber.matches(value) || value.toDouble() <= 0)) {
            throw IllegalArgumentException(message)
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
                        return MISSING_AUDIO_LANGUAGE
                    }

                    "Subtitle" -> if (!ignoreSubtitle) {
                        return MISSING_SUBTITLE_LANGUAGE
                    }
                }
            }
        }
        return null
    }

    private fun attachmentMapping(streams: Map<String, List<Stream>>) = if (streams.keys.contains("Attachment")) {
        "$ATTACHMENT_MAPPING "
    } else {
        ""
    }

    private fun escape(filename: String): String = unsafeShellCharacters.replace(filename) { "\\${it.value}" }

    private fun dockerWrap(command: String, unstarted: Boolean, deinterlace: Boolean): String {
        val dockerCommand = if (unstarted) {
            "create"
        } else {
            "run"
        }

        val weightsVolume = if (deinterlace) {
            "-v $DOCKER_WEIGHTS_VOLUME "
        } else {
            ""
        }

        return "docker $dockerCommand --rm -it -v $DOCKER_CONFIG_VOLUME ${weightsVolume}$DOCKER_IMAGE $command"
    }

    private fun mkvmergeDockerWrap(arguments: String, unstarted: Boolean): String {
        val dockerCommand = if (unstarted) {
            "create"
        } else {
            "run"
        }

        return "docker $dockerCommand --rm -it -v $DOCKER_CONFIG_VOLUME --entrypoint $MKVMERGE $MKVMERGE_DOCKER_IMAGE $arguments"
    }

    private fun languageList(parameters: Map<String, String>): List<String> = defaultLanguages.plus(
        parameters[ADDITIONAL_LANGUAGES]
            ?.split(",")
            ?: emptyList()
    )

    private fun videoFormat(
        streams: Map<String, List<Stream>>,
        deinterlace: Boolean,
        weightsDir: String,
        reduceRam: Boolean
    ): String {
        val encoderOptions = if (reduceRam) {
            ENCODER_OPTIONS + LOW_PARALLELISM
        } else {
            ENCODER_OPTIONS
        }

        return if (deinterlace) {
            "$VIDEO_ENCODER $GOP_DEINTERLACED $encoderOptions " +
                "-vf \"nnedi=weights=$weightsDir/$WEIGHTS_FILE_NAME:$NNEDI_OPTIONS\""
        } else if (streams["Video"]!!.first().codec.startsWith("av1")) {
            COPY
        } else {
            "$VIDEO_ENCODER $GOP_PROGRESSIVE $encoderOptions"
        }
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
