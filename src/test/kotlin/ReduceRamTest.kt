import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReduceRamTest {

    private val lowRamEncoding = "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le " +
        "-svtav1-params tune=0:enable-qm=1:enable-variance-boost=1:lp=3"

    private val source = """
            Input #0, matroska,webm, from 'somefile.mkv':
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """

    private val sourceWithSubtitle = """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
        """

    private val av1Source = """
            Stream #0:0(eng): Video: av1 (libdav1d) (Main), yuv420p(tv, bt709, progressive), 1920x1080, SAR 1:1 DAR 16:9, 23.98 fps, 23.98 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """

    private val interlacedSource = """
            Stream #0:0(deu): Video: mpeg2video (Main), yuv420p(tv, top first), 720x576 [SAR 64:45 DAR 16:9], 25 fps, 25 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
        """

    @Test
    fun `limits encoder parallelism and decoder threads`() {
        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv", "-reduceRAM"))

        assertEquals(
            "ffmpeg -n -threads 4 -i somefile.mkv $lowRamEncoding -map 0:a:0 -c:a:0 copy -metadata:s:a:0 title=\"Stereo (AC3)\" " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `limits decoder threads only on the video input when trimming`() {
        val command = CommandCreator(FakeWrapper(sourceWithSubtitle))
            .doAction(arrayOf("somefile.mkv", "-reduceRAM", "-trimStart=28"))

        assertEquals(
            true,
            command.lines().last().startsWith("ffmpeg -n -threads 4 -ss 28 -i somefile.mkv -ss 28 -i somefile.mkv $lowRamEncoding "),
            command
        )
    }

    @Test
    fun `limits parallelism while deinterlacing`() {
        val command = CommandCreator(FakeWrapper(interlacedSource), FakeWeightsInstaller())
            .doAction(arrayOf("somefile.mkv", "-reduceRAM", "-deinterlace"))

        assertEquals(
            true,
            command.startsWith(
                "ffmpeg -n -threads 4 -i somefile.mkv -map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 " +
                    "-pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1:lp=3 -vf \"nnedi="
            ),
            command
        )
    }

    @Test
    fun `limits parallelism only in the ffmpeg pass of two-pass transcode`() {
        val lines = CommandCreator(FakeWrapper(source))
            .doAction(arrayOf("somefile.mkv", "-reduceRAM", "-twoPassTranscode"))
            .lines()

        assertEquals(true, lines[0].startsWith("ffmpeg -n -threads 4 -i somefile.mkv $lowRamEncoding "), lines[0])
        assertEquals(false, lines[1].contains("threads"), lines[1])
        assertEquals(false, lines[1].contains("lp=3"), lines[1])
    }

    @Test
    fun `limits parallelism in docker mode`() {
        val command = CommandCreator(FakeWrapper(source))
            .doAction(arrayOf("somefile.mkv", "-docker", "-reduceRAM"))

        assertEquals(
            true,
            command.startsWith(
                "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -threads 4 -i /config/somefile.mkv $lowRamEncoding "
            ),
            command
        )
    }

    @Test
    fun `rejects copied av1 video`() {
        val command = CommandCreator(FakeWrapper(av1Source)).doAction(arrayOf("somefile.mkv", "-reduceRAM"))

        assertEquals("[Error] -reduceRAM cannot be used on AV1 sources, the copied video stream is not encoded", command)
    }

    @Test
    fun `keeps default parallelism without the flag`() {
        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv"))

        assertEquals(false, command.contains("-threads"), command)
        assertEquals(false, command.contains("lp="), command)
    }
}
