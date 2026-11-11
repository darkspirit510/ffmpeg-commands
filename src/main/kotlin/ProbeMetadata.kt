// Values from the `ffmpeg -i` output beyond the stream lines: the duration of the input and the BPS tag (bits per
// second) that mkvmerge writes for every track. Older mkvmerge versions wrote it as BPS-eng.
class ProbeMetadata(
    private val lines: List<String>
) {
    private val streamLine = Regex("""^Stream #0:(\d+)\b.*""")
    private val bpsLine = Regex("""^BPS(-\w+)?\s*:\s*(\d+)$""")
    private val durationLine = Regex("""^Duration: (\d+):(\d{2}):(\d{2}(\.\d+)?),.*""")

    private val bitrates: Map<Int, Long> by lazy {
        val result = mutableMapOf<Int, Long>()
        var stream: Int? = null

        lines.map { it.trim() }.forEach { line ->
            val streamMatch = streamLine.matchEntire(line)
            val bpsMatch = bpsLine.matchEntire(line)

            if (streamMatch != null) {
                stream = streamMatch.groupValues[1].toInt()
            } else if (bpsMatch != null && stream != null) {
                result.putIfAbsent(stream!!, bpsMatch.groupValues[2].toLong())
            }
        }

        result
    }

    fun bitrate(streamIndex: Int): Long? = bitrates[streamIndex]

    // In seconds, null when ffmpeg reports none (Duration: N/A).
    fun duration(): Double? = lines.firstNotNullOfOrNull { durationLine.matchEntire(it.trim()) }?.let {
        it.groupValues[1].toInt() * 3600.0 + it.groupValues[2].toInt() * 60.0 + it.groupValues[3].toDouble()
    }
}
