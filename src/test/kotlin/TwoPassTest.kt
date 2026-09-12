import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwoPassTest {

    @Test
    fun `generates two-pass commands for basic file`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -crf 17 -preset 2 -f matroska Output/somefile_video.mkv" +
            "\n" +
            "ffmpeg -n -i somefile.mkv -i Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `generates two-pass commands with multiple audio streams`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -crf 17 -preset 2 -f matroska Output/somefile_video.mkv" +
            "\n" +
            "ffmpeg -n -i somefile.mkv -i Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy -map 0:a:1 -c:a:0 copy -map 0:a:0 -c:a:1 copy -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `generates two-pass commands with subtitles`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -crf 17 -preset 2 -f matroska Output/somefile_video.mkv" +
            "\n" +
            "ffmpeg -n -i somefile.mkv -i Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy -map 0:s:0 -c:s:0 copy -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `generates two-pass commands with docker`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/somefile.mkv -map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -crf 17 -preset 2 -f matroska /config/Output/somefile_video.mkv" +
            "\n" +
            "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/somefile.mkv -i /config/Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv",
            command
        )
    }
}
