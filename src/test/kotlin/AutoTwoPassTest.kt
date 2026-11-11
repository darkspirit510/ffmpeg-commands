import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// The muxer buffers video 20 Mb/s + copied DTS 3 Mb/s + AC3 encode 0.64 Mb/s = 2.955 MB per second of subtitle gap,
// so the default threshold of 8 GB is crossed by a gap of about 2707 s.
class AutoTwoPassTest {

    private val videoEncoding = "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le " +
        "-svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"

    private val singlePass = "ffmpeg -n -i somefile.mkv $videoEncoding " +
        "-map 0:a:0 -c:a:0 copy -metadata:s:a:0 title=\"Surround 5.1 (DTS-HD MA)\" " +
        "-map 0:a:0 -c:a:1 ac3 -metadata:s:a:1 title=\"Surround 5.1 (AC3)\" " +
        "-map 0:s:0 -c:s:0 copy -crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
        "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv"

    private val twoPass = "ffmpeg -n -i somefile.mkv $videoEncoding " +
        "-map 0:a:0 -c:a:0 ac3 -metadata:s:a:0 title=\"Surround 5.1 (AC3)\" " +
        "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION -map_chapters -1 -map_metadata:g -1 " +
        "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile_intermediate.mkv" +
        "\n" +
        "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
        "-D -a 1 --track-name 1:\"Surround 5.1 (DTS-HD MA)\" -s 2 somefile.mkv --track-order 0:0,1:1,0:1,1:2"

    private val source = """
            Input #0, matroska,webm, from 'somefile.mkv':
              Duration: 02:00:00.00, start: 0.000000, bitrate: 23640 kb/s
              Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn
                Metadata:
                  BPS             : 20000000
              Stream #0:1(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
                Metadata:
                  BPS             : 3000000
              Stream #0:2(deu): Subtitle: subrip (srt)
                Metadata:
                  BPS             : 10
              Stream #0:3(fra): Subtitle: subrip (srt)
        """

    private fun run(cues: Map<Int, List<Double>>?, vararg options: String) =
        CommandCreator(FakeWrapper(source), FakeCueReader(cues)).doAction(arrayOf("somefile.mkv", *options))

    @Test
    fun `stays single pass while the longest subtitle gap fits below the threshold`() {
        val command = run(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0)))

        assertEquals(singlePass, command)
    }

    @Test
    fun `switches to two passes and prints why when a gap between lines exceeds the threshold`() {
        val command = run(mapOf(2 to listOf(56.0, 3100.0, 6000.0)))

        assertEquals(
            "# Two passes: subtitle stream #0:2 goes 3044 s without a line, a single-pass mux would buffer about " +
                "9.0 GB (threshold 8 GB)\n$twoPass",
            command
        )
    }

    @Test
    fun `counts the gap before the first line`() {
        val command = run(mapOf(2 to listOf(3500.0, 5000.0, 7000.0)))

        assertEquals(
            "# Two passes: subtitle stream #0:2 goes 3500 s without a line, a single-pass mux would buffer about " +
                "10.3 GB (threshold 8 GB)",
            command.lines()[0]
        )
    }

    @Test
    fun `counts the gap after the last line until the end of the file`() {
        val command = run(mapOf(2 to listOf(56.0, 2000.0, 4000.0)))

        assertEquals(
            "# Two passes: subtitle stream #0:2 goes 3200 s without a line, a single-pass mux would buffer about " +
                "9.5 GB (threshold 8 GB)",
            command.lines()[0]
        )
    }

    @Test
    fun `treats a selected subtitle track without cues as one gap over the whole file`() {
        val command = run(mapOf(2 to emptyList(), 3 to listOf(56.0)))

        assertEquals(
            "# Two passes: subtitle stream #0:2 goes 7200 s without a line, a single-pass mux would buffer about " +
                "21.3 GB (threshold 8 GB)",
            command.lines()[0]
        )
    }

    @Test
    fun `ignores gaps of subtitle tracks that are not selected`() {
        val command = run(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0), 3 to emptyList()))

        assertEquals(singlePass, command)
    }

    @Test
    fun `takes a higher threshold from the parameter`() {
        val command = run(mapOf(2 to listOf(56.0, 3100.0, 6000.0)), "-twoPassThreshold=10")

        assertEquals(singlePass, command)
    }

    @Test
    fun `takes a decimal threshold below the default from the parameter`() {
        val command = run(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0)), "-twoPassThreshold=0.5")

        assertEquals(
            "# Two passes: subtitle stream #0:2 goes 2000 s without a line, a single-pass mux would buffer about " +
                "5.9 GB (threshold 0.5 GB)\n$twoPass",
            command
        )
    }

    @Test
    fun `forced two passes win over the threshold and print no reason`() {
        val cueReader = FakeCueReader(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0)))
        val command = CommandCreator(FakeWrapper(source), cueReader)
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-twoPassThreshold=100"))

        assertEquals(twoPass, command)
        assertEquals(emptyList(), cueReader.requestedFiles)
    }

    @Test
    fun `checks nothing when subtitles are dropped`() {
        val cueReader = FakeCueReader(mapOf(2 to emptyList()))
        val command = CommandCreator(FakeWrapper(source), cueReader).doAction(arrayOf("somefile.mkv", "-dropSubtitles"))

        assertEquals(false, command.startsWith("#"), command)
        assertEquals(emptyList(), cueReader.requestedFiles)
    }

    @Test
    fun `reads the cues of the unescaped source file`() {
        val cueReader = FakeCueReader(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0)))
        CommandCreator(FakeWrapper(source), cueReader).doAction(arrayOf("some file.mkv", "-docker"))

        assertEquals(listOf("some file.mkv"), cueReader.requestedFiles)
    }

    @Test
    fun `warns and stays single pass without subtitle cues`() {
        assertEquals("# Single pass, subtitle gaps not checked: no subtitle cues in the source\n$singlePass", run(null))
        assertEquals(
            "# Single pass, subtitle gaps not checked: no subtitle cues in the source\n$singlePass",
            run(mapOf(2 to emptyList(), 3 to emptyList()))
        )
    }

    @Test
    fun `warns and stays single pass for a source that is not Matroska`() {
        val cueReader = FakeCueReader(mapOf(2 to emptyList()))
        val command = CommandCreator(FakeWrapper(source.replace("matroska,webm", "mpegts")), cueReader)
            .doAction(arrayOf("somefile.mkv"))

        assertEquals("# Single pass, subtitle gaps not checked: not a Matroska source\n$singlePass", command)
        assertEquals(emptyList(), cueReader.requestedFiles)
    }

    @Test
    fun `warns and stays single pass when a buffered stream has no BPS tag`() {
        val command = CommandCreator(
            FakeWrapper(source.replace("BPS             : 3000000", "DURATION        : 02:00:00.000000000")),
            FakeCueReader(mapOf(2 to listOf(56.0)))
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals("# Single pass, subtitle gaps not checked: no BPS tag on stream #0:1\n$singlePass", command)
    }

    @Test
    fun `reads the BPS tag of older mkvmerge versions`() {
        val command = CommandCreator(
            FakeWrapper(source.replace("BPS             :", "BPS-eng         :")),
            FakeCueReader(mapOf(2 to listOf(56.0, 3100.0, 6000.0)))
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(true, command.startsWith("# Two passes: subtitle stream #0:2 goes 3044 s"), command)
    }

    @Test
    fun `rejects trimStart when two passes are needed`() {
        val command = run(mapOf(2 to listOf(56.0, 3100.0, 6000.0)), "-trimStart=28")

        assertEquals(
            "[Error] -trimStart cannot be used with two passes yet, but subtitle stream #0:2 goes 3044 s without a " +
                "line, a single-pass mux would buffer about 9.0 GB (threshold 8 GB). Use -dropSubtitles, see issue #27",
            command
        )
    }

    @Test
    fun `trims in a single pass when the gaps are short`() {
        val command = run(mapOf(2 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0)), "-trimStart=28")

        assertEquals(true, command.startsWith("ffmpeg -n -ss 28 -i somefile.mkv -ss 28 -i somefile.mkv "), command)
    }

    @Test
    fun `fails on a threshold that is not a positive number`() {
        listOf("", "0", "0.0", "-1", "abc", "1,5").forEach { value ->
            val command = run(mapOf(2 to listOf(56.0)), "-twoPassThreshold=$value")

            assertEquals("[Error] twoPassThreshold must be a positive number of GB", command, "value: $value")
        }
    }

    @Test
    fun `fails on a threshold without value`() {
        assertEquals("[Error] twoPassThreshold must be a positive number of GB", run(null, "-twoPassThreshold"))
    }

    @Test
    fun `warns and stays single pass without a duration, as the gap after the last line is unknown`() {
        val command = CommandCreator(
            FakeWrapper(source.replace("Duration: 02:00:00.00", "Duration: N/A")),
            FakeCueReader(mapOf(2 to listOf(56.0)))
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals("# Single pass, subtitle gaps not checked: no duration in the source\n$singlePass", command)
    }

    @Test
    fun `measures the gaps from the cut when trimming`() {
        val command = run(mapOf(2 to listOf(2900.0, 4000.0, 6000.0, 7000.0)), "-trimStart=900")

        assertEquals(true, command.startsWith("ffmpeg -n -ss 900 -i somefile.mkv "), command)
    }

    @Test
    fun `ignores lines before the cut when trimming`() {
        val command = run(mapOf(2 to listOf(100.0, 3500.0, 5000.0, 7000.0)), "-trimStart=200")

        assertEquals(
            "[Error] -trimStart cannot be used with two passes yet, but subtitle stream #0:2 goes 3300 s without a " +
                "line, a single-pass mux would buffer about 9.8 GB (threshold 8 GB). Use -dropSubtitles, see issue #27",
            command
        )
    }
}
