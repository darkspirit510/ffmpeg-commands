import java.util.Locale

private const val AC3_BITS_PER_SECOND = 640_000L
private const val BITS_PER_BYTE = 8
const val BYTES_PER_GB = 1e9

// Estimates how much RAM a single ffmpeg mux needs. ffmpeg's scheduler feeds the output stream that is furthest behind,
// so a subtitle track without a packet for a long time holds back everything else; with -max_interleave_delta 0 the
// muxer buffers every other output stream until the next subtitle line, the video included. The source video bitrate
// stands in for the encoded video, which overestimates on purpose.
class SubtitleGapCheck(
    private val cueReader: CueReader
) {
    sealed class Outcome {
        object NothingToCheck : Outcome()

        class NotChecked(
            val reason: String
        ) : Outcome()

        class Estimated(
            val streamIndex: Int,
            val gapSeconds: Double,
            val bytes: Double
        ) : Outcome() {
            fun describe(thresholdGb: String): String = String.format(
                Locale.ROOT,
                "subtitle stream #0:%d goes %.0f s without a line, a single-pass mux would buffer about %.1f GB " +
                    "(threshold %s GB)",
                streamIndex,
                gapSeconds,
                bytes / BYTES_PER_GB,
                thresholdGb
            )
        }
    }

    fun check(
        file: String,
        probe: List<String>,
        matroska: Boolean,
        start: Double,
        videoStreamIndex: Int,
        audio: List<Mapping>,
        subtitles: List<Mapping>
    ): Outcome {
        if (subtitles.isEmpty()) {
            return Outcome.NothingToCheck
        }

        if (!matroska) {
            return Outcome.NotChecked("not a Matroska source")
        }

        val cues = cueReader.subtitleCueTimes(file)

        if (cues == null || cues.values.all { it.isEmpty() }) {
            return Outcome.NotChecked("no subtitle cues in the source")
        }

        val metadata = ProbeMetadata(probe)
        val duration = metadata.duration() ?: return Outcome.NotChecked("no duration in the source")
        val bufferedStreams = listOf(videoStreamIndex) + audio.filter { it.action == COPY }.map { it.streamIndex }
        val missingBitrate = bufferedStreams.firstOrNull { metadata.bitrate(it) == null }

        if (missingBitrate != null) {
            return Outcome.NotChecked("no BPS tag on stream #0:$missingBitrate")
        }

        val bitsPerSecond = bufferedStreams.sumOf { metadata.bitrate(it) ?: 0L } +
            audio.count { it.action != COPY } * AC3_BITS_PER_SECOND

        val (streamIndex, gap) = subtitles
            .map { it.streamIndex to longestGap(cues[it.streamIndex] ?: emptyList(), start, duration) }
            .maxBy { it.second }

        return Outcome.Estimated(streamIndex, gap, gap * bitsPerSecond / BITS_PER_BYTE)
    }

    // Before the first line, between lines and after the last line until the end of the file; with -trimStart, the
    // output starts at the cut and earlier lines are dropped.
    private fun longestGap(times: List<Double>, start: Double, duration: Double): Double {
        val points = listOf(start) + times.filter { it >= start } + duration

        return points.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0.0
    }
}
