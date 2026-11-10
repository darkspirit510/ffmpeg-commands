// Builds the output title of an audio stream, e.g. "Surround 5.1 (AC3)", from the codec part of an ffmpeg stream line.
object AudioTitle {

    private val layoutNames = mapOf(
        "mono" to "Mono",
        "stereo" to "Stereo",
        "downmix" to "Stereo",
        "2.1" to "Stereo 2.1",
        "3.0" to "Surround 3.0",
        "3.0(back)" to "Surround 3.0",
        "4.0" to "Surround 4.0",
        "quad" to "Surround 4.0",
        "quad(side)" to "Surround 4.0",
        "3.1" to "Surround 3.1",
        "4.1" to "Surround 4.1",
        "5.0" to "Surround 5.0",
        "5.0(side)" to "Surround 5.0",
        "5.1" to "Surround 5.1",
        "5.1(side)" to "Surround 5.1",
        "6.0" to "Surround 6.0",
        "6.0(front)" to "Surround 6.0",
        "hexagonal" to "Surround 6.0",
        "6.1" to "Surround 6.1",
        "6.1(back)" to "Surround 6.1",
        "6.1(front)" to "Surround 6.1",
        "7.0" to "Surround 7.0",
        "7.0(front)" to "Surround 7.0",
        "7.1" to "Surround 7.1",
        "7.1(wide)" to "Surround 7.1",
        "7.1(wide-side)" to "Surround 7.1",
        "octagonal" to "Surround 8.0"
    )

    private val codecNames = mapOf(
        "ac3" to "AC3",
        "eac3" to "E-AC3",
        "truehd" to "TrueHD",
        "aac" to "AAC",
        "flac" to "FLAC",
        "opus" to "Opus",
        "vorbis" to "Vorbis",
        "mp3" to "MP3",
        "mp2" to "MP2"
    )

    private val dtsProfileNames = mapOf(
        "DTS" to "DTS",
        "DTS-ES" to "DTS",
        "DTS 96/24" to "DTS",
        "DTS-HD MA" to "DTS-HD MA",
        "DTS-HD MA + DTS:X" to "DTS:X",
        "DTS-HD HRA" to "DTS-HD HRA"
    )

    private val bracket = Regex("""\(([^)]*)\)""")

    fun forStream(codec: String): String {
        val fields = codec.split(", ")
        val layoutName = layoutName(fields.getOrElse(2) { "" })
        val codecName = codecName(fields[0]) ?: throw IllegalArgumentException("unknown audio codec '${fields[0]}'")

        return "$layoutName ($codecName)"
    }

    // An AC3 copy of a 6.x, 7.x or 8.0 source is downmixed to 5.1, the most channels AC3 can carry.
    fun forAc3Copy(codec: String): String {
        val layoutName = if (needsAc3Downmix(codec)) {
            "Surround 5.1"
        } else {
            layoutName(codec.split(", ").getOrElse(2) { "" })
        }

        return "$layoutName (AC3)"
    }

    fun needsAc3Downmix(codec: String): Boolean {
        val layout = codec.split(", ").getOrElse(2) { "" }

        return layoutName(layout) in ac3DownmixedLayoutNames
    }

    private val ac3DownmixedLayoutNames = setOf("Surround 6.0", "Surround 6.1", "Surround 7.0", "Surround 7.1", "Surround 8.0")

    // Without a layout in the file, ffmpeg prints "6 channels" and assumes its default layout for that count.
    private val channelCountLayouts = mapOf(
        "1 channels" to "mono",
        "2 channels" to "stereo",
        "3 channels" to "2.1",
        "4 channels" to "4.0",
        "5 channels" to "5.0",
        "6 channels" to "5.1",
        "7 channels" to "6.1",
        "8 channels" to "7.1"
    )

    private fun layoutName(layout: String): String =
        layoutNames[channelCountLayouts[layout] ?: layout]
            ?: throw IllegalArgumentException("unknown channel layout '$layout'")

    private fun codecName(codecField: String): String? {
        val name = codecField.substringBefore(" ")
        // ffmpeg may print the decoder before the profile, e.g. "dts (dca) (DTS-HD MA)", so every bracket counts.
        val brackets = bracket.findAll(codecField).map { it.groupValues[1] }.toList()

        if (name == "dts") {
            return brackets.firstNotNullOfOrNull { dtsProfileNames[it] }
        }

        if (name.startsWith("pcm_")) {
            return "PCM"
        }

        val baseName = codecNames[name]

        if (baseName != null && brackets.any { it.contains("Atmos") }) {
            return "$baseName Atmos"
        }

        return baseName
    }
}
