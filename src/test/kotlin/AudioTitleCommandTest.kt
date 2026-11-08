import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AudioTitleCommandTest {

    private val video =
        "Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn"

    @Test
    fun `titles every audio stream and downmixes the AC3 copy of a 7_1 source`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                $video
                Stream #0:1(deu): Audio: truehd (Dolby TrueHD + Dolby Atmos), 48000 Hz, 7.1, s32 (24 bit) (default)
                """
            )
        ).doAction(arrayOf("movie.mkv"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-map 0:a:0 -c:a:0 copy -metadata:s:a:0 title=\"Surround 7.1 (TrueHD Atmos)\" " +
                "-map 0:a:0 -c:a:1 ac3 -ac:a:1 6 -metadata:s:a:1 title=\"Surround 5.1 (AC3)\" " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }

    @Test
    fun `fails on an unknown channel layout of a mapped audio stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                $video
                Stream #0:1(deu): Audio: ac3, 48000 Hz, 6 channels, fltp, 640 kb/s
                """
            )
        ).doAction(arrayOf("movie.mkv"))

        assertEquals(
            "[Error] Audio stream #0:1: unknown channel layout '6 channels' (ac3, 48000 Hz, 6 channels, fltp, 640 kb/s)",
            command
        )
    }

    @Test
    fun `ignores an unknown channel layout of a dropped audio stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                $video
                Stream #0:1(ita): Audio: ac3, 48000 Hz, 6 channels, fltp, 640 kb/s
                Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                """
            )
        ).doAction(arrayOf("movie.mkv"))

        assertEquals(
            "ffmpeg -n -i movie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-map 0:a:1 -c:a:0 copy -metadata:s:a:0 title=\"Stereo (AC3)\" " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/movie.mkv",
            command
        )
    }
}
