import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TrimStartTest {

    private val videoEncoding = "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le " +
        "-svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"

    private val source = """
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

    @Test
    fun `seeks input and discards packets before the cut`() {
        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv", "-trimStart=28"))

        assertEquals(
            "ffmpeg -n -ss 28 -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-copypriorss 0 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `reads subtitles through their own seeking input`() {
        val command = CommandCreator(FakeWrapper(sourceWithSubtitle))
            .doAction(arrayOf("somefile.mkv", "-trimStart=28"))

        assertEquals(
            "ffmpeg -n -ss 28 -i somefile.mkv -ss 28 -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 copy " +
                "-map 1:s:0 -c:s:0 copy -crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-copypriorss 0 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `reads subtitles through their own input in two-pass mux command only`() {
        val command = CommandCreator(FakeWrapper(sourceWithSubtitle))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-trimStart=28"))

        assertEquals(
            "ffmpeg -n -ss 28 -i somefile.mkv $videoEncoding -crf 17 -preset 2 -f matroska Output/somefile_video.mkv" +
                "\n" +
                "ffmpeg -n -ss 28 -i somefile.mkv -i Output/somefile_video.mkv -ss 28 -i somefile.mkv " +
                "-map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy -map 2:s:0 -c:s:0 copy " +
                "-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-copypriorss 0 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `adds no subtitle input when subtitles are dropped`() {
        val command = CommandCreator(FakeWrapper(sourceWithSubtitle))
            .doAction(arrayOf("somefile.mkv", "-dropSubtitles", "-trimStart=28"))

        assertEquals(true, command.startsWith("ffmpeg -n -ss 28 -i somefile.mkv -map 0:v:0 "), command)
    }

    @Test
    fun `keeps subtitles on the source input without trimStart`() {
        val command = CommandCreator(FakeWrapper(sourceWithSubtitle)).doAction(arrayOf("somefile.mkv"))

        assertEquals(true, command.startsWith("ffmpeg -n -i somefile.mkv -map 0:v:0 "), command)
        assertEquals(true, command.contains("-map 0:s:0 -c:s:0 copy"), command)
    }

    @Test
    fun `accepts decimal seconds`() {
        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv", "-trimStart=28.5"))

        assertEquals(true, command.startsWith("ffmpeg -n -ss 28.5 -i somefile.mkv "), command)
    }

    @Test
    fun `seeks source in both two-pass commands but never the trimmed video`() {
        val command = CommandCreator(FakeWrapper(source))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-trimStart=28"))

        assertEquals(
            "ffmpeg -n -ss 28 -i somefile.mkv $videoEncoding -crf 17 -preset 2 -f matroska Output/somefile_video.mkv" +
                "\n" +
                "ffmpeg -n -ss 28 -i somefile.mkv -i Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-copypriorss 0 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `trims in docker mode`() {
        val command = CommandCreator(FakeWrapper(source))
            .doAction(arrayOf("somefile.mkv", "-docker", "-trimStart=28"))

        assertEquals(
            true,
            command.startsWith(
                "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -ss 28 -i /config/somefile.mkv "
            ),
            command
        )
    }

    @Test
    fun `rejects copied av1 video`() {
        val command = CommandCreator(FakeWrapper(av1Source)).doAction(arrayOf("somefile.mkv", "-trimStart=28"))

        assertEquals(
            "[Error] -trimStart cannot be used on AV1 sources, a copied video stream can only be cut at a keyframe",
            command
        )
    }

    @Test
    fun `trims av1 source when deinterlacing re-encodes it`() {
        val command = CommandCreator(FakeWrapper(av1Source), FakeWeightsInstaller())
            .doAction(arrayOf("somefile.mkv", "-deinterlace", "-trimStart=28"))

        assertEquals(true, command.startsWith("ffmpeg -n -ss 28 -i somefile.mkv "), command)
    }

    @Test
    fun `fails on anything but positive seconds`() {
        listOf("", "0", "0.0", "-5", "00:28", "1:30", "abc", "1e3", ".5", "28.").forEach { value ->
            val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv", "-trimStart=$value"))

            assertEquals("[Error] trimStart must be a positive number of seconds", command, "value: $value")
        }
    }

    @Test
    fun `fails on trimStart without value`() {
        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv", "-trimStart"))

        assertEquals("[Error] trimStart must be a positive number of seconds", command)
    }
}
