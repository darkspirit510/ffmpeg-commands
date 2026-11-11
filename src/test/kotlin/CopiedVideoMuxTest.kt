import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// AV1 sources keep their video. With nothing to encode, mkvmerge alone remuxes them; an AC3 encode needs an ffmpeg pass.
class CopiedVideoMuxTest {

    private val matroskaInput = "Input #0, matroska,webm, from 'somefile.mkv':"

    private val sparseCues = mapOf(3 to listOf(3500.0, 7000.0), 4 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0))
    private val denseCues = mapOf(3 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0), 4 to listOf(56.0, 2000.0, 4000.0, 6000.0, 7000.0))

    private val ac3Source = """
            $matroskaInput
              Duration: 02:00:00.00, start: 0.000000, bitrate: 21280 kb/s
              Stream #0:0(eng): Video: av1 (libdav1d) (Main), yuv420p10le(tv, bt709, progressive), 1920x1080, 23.98 fps, 23.98 tbr, 1k tbn
                Metadata:
                  BPS             : 20000000
              Stream #0:1(deu): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
                Metadata:
                  BPS             : 640000
              Stream #0:2(eng): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
                Metadata:
                  BPS             : 640000
              Stream #0:3(eng): Subtitle: subrip (srt)
              Stream #0:4(deu): Subtitle: subrip (srt)
        """

    private val dtsSource = """
            $matroskaInput
              Duration: 02:00:00.00, start: 0.000000, bitrate: 23000 kb/s
              Stream #0:0(eng): Video: av1 (libdav1d) (Main), yuv420p10le(tv, bt709, progressive), 1920x1080, 23.98 fps, 23.98 tbr, 1k tbn
                Metadata:
                  BPS             : 20000000
              Stream #0:1(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
                Metadata:
                  BPS             : 3000000
              Stream #0:2(eng): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
                Metadata:
                  BPS             : 640000
              Stream #0:3(eng): Subtitle: subrip (srt)
              Stream #0:4(deu): Subtitle: subrip (srt)
        """

    private val dtsSinglePass = "ffmpeg -n -i somefile.mkv -map 0:v:0 -c:v:0 copy " +
        "-map 0:a:0 -c:a:0 copy -metadata:s:a:0 title=\"Surround 5.1 (DTS-HD MA)\" " +
        "-map 0:a:0 -c:a:1 ac3 -metadata:s:a:1 title=\"Surround 5.1 (AC3)\" " +
        "-map 0:a:1 -c:a:2 copy -metadata:s:a:2 title=\"Surround 5.1 (AC3)\" " +
        "-map 0:s:1 -c:s:0 copy -map 0:s:0 -c:s:1 copy " +
        "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv"

    private val dtsTwoPass = "ffmpeg -n -i somefile.mkv -map 0:a:0 -c:a:0 ac3 -metadata:s:a:0 title=\"Surround 5.1 (AC3)\" " +
        "-map_chapters -1 -map_metadata:g -1 -max_muxing_queue_size 9999 -max_interleave_delta 0 " +
        "Output/somefile_intermediate.mkv" +
        "\n" +
        "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv -d 0 -a 1,2 " +
        "--track-name 1:\"Surround 5.1 (DTS-HD MA)\" --track-name 2:\"Surround 5.1 (AC3)\" -s 4,3 somefile.mkv " +
        "--track-order 1:0,1:1,0:0,1:2,1:4,1:3"

    @Test
    fun `remuxes with mkvmerge alone when no audio track needs an encode`() {
        val cueReader = FakeCueReader(sparseCues)
        val command = CommandCreator(FakeWrapper(ac3Source), cueReader).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv -d 0 -a 1,2 " +
                "--track-name 1:\"Surround 5.1 (AC3)\" --track-name 2:\"Surround 5.1 (AC3)\" -s 4,3 somefile.mkv " +
                "--track-order 0:0,0:1,0:2,0:4,0:3",
            command
        )
        assertEquals(emptyList(), cueReader.requestedFiles)
    }

    @Test
    fun `sets missing audio languages in the mkvmerge remux`() {
        val command = CommandCreator(
            FakeWrapper(ac3Source.replace("Stream #0:1(deu)", "Stream #0:1")),
            FakeCueReader(denseCues)
        ).doAction(arrayOf("somefile.mkv", "-setAudioLanguages=deu", "-dropSubtitles"))

        assertEquals(
            "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv -d 0 -a 1,2 --language 1:deu " +
                "--track-name 1:\"Surround 5.1 (AC3)\" --track-name 2:\"Surround 5.1 (AC3)\" -S somefile.mkv " +
                "--track-order 0:0,0:1,0:2",
            command
        )
    }

    @Test
    fun `runs the mkvmerge remux in docker`() {
        val command = CommandCreator(FakeWrapper(ac3Source), FakeCueReader(denseCues))
            .doAction(arrayOf("somefile.mkv", "-docker"))

        assertEquals(
            "[ ! -e Output/somefile.mkv ] && docker run --rm -it -v \"\$(pwd)\":/config --entrypoint mkvmerge " +
                "jlesage/mkvtoolnix -o /config/Output/somefile.mkv -d 0 -a 1,2 " +
                "--track-name 1:\"Surround 5.1 (AC3)\" --track-name 2:\"Surround 5.1 (AC3)\" -s 4,3 /config/somefile.mkv " +
                "--track-order 0:0,0:1,0:2,0:4,0:3",
            command
        )
    }

    @Test
    fun `keeps the single ffmpeg command for an ac3 encode while the gaps are short`() {
        val command = CommandCreator(FakeWrapper(dtsSource), FakeCueReader(denseCues)).doAction(arrayOf("somefile.mkv"))

        assertEquals(dtsSinglePass, command)
    }

    // 20 Mb/s video + 3 Mb/s DTS + 0.64 Mb/s copied AC3 + 0.64 Mb/s AC3 encode = 3.035 MB/s over 3500 s.
    @Test
    fun `encodes only the ac3 track with ffmpeg and muxes the rest with mkvmerge when the gaps are long`() {
        val command = CommandCreator(FakeWrapper(dtsSource), FakeCueReader(sparseCues)).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "# Two passes: subtitle stream #0:3 goes 3500 s without a line, a single-pass mux would buffer about " +
                "10.6 GB (threshold 8 GB)\n$dtsTwoPass",
            command
        )
    }

    @Test
    fun `encodes only the ac3 track with ffmpeg when two passes are forced`() {
        val command = CommandCreator(FakeWrapper(dtsSource), FakeCueReader(denseCues))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(dtsTwoPass, command)
    }

    @Test
    fun `keeps the single ffmpeg command for a source that is not Matroska`() {
        val command = CommandCreator(
            FakeWrapper(ac3Source.replace("matroska,webm", "mpegts")),
            FakeCueReader(sparseCues)
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy -metadata:s:a:0 title=\"Surround 5.1 (AC3)\" " +
                "-map 0:a:1 -c:a:1 copy -metadata:s:a:1 title=\"Surround 5.1 (AC3)\" " +
                "-map 0:s:1 -c:s:0 copy -map 0:s:0 -c:s:1 copy " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }
}
