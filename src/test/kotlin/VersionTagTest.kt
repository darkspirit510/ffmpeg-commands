import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VersionTagTest {

    private val h264Source = """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """

    @Test
    fun `tags video stream with tool version in single command`() {
        val command = CommandCreator(FakeWrapper(h264Source)).doAction(arrayOf("movie.mkv"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=1.0 " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }

    @Test
    fun `tags only the mux command in two-pass mode`() {
        val command = CommandCreator(FakeWrapper(h264Source)).doAction(arrayOf("movie.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-crf 17 -preset 2 -f matroska Output/movie_video.mkv" +
                "\n" +
                "ffmpeg -n -i movie.mkv -i Output/movie_video.mkv -map 1:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }

    @Test
    fun `tags deinterlaced encode`() {
        val interlacedSource = """
            Stream #0:0(deu): Video: mpeg2video (Main), yuv420p(tv, top first), 720x576 [SAR 64:45 DAR 16:9], 25 fps, 25 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
        """

        val command = CommandCreator(FakeWrapper(interlacedSource), FakeWeightsInstaller())
            .doAction(arrayOf("movie.mkv", "-deinterlace"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-vf \"nnedi=weights=\$HOME/.cache/ffmpeg-commands/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\" " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }

    @Test
    fun `does not tag copied av1 video`() {
        val av1Source = """
            Stream #0:0(eng): Video: av1 (libdav1d) (Main), yuv420p10le(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """

        val command = CommandCreator(FakeWrapper(av1Source)).doAction(arrayOf("movie.mkv"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }
}
