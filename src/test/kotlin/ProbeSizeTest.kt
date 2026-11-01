import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProbeSizeTest {

    private val videoEncoding = "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le " +
        "-svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"

    private val sourceWithLateSubtitle = """
            [in#0/matroska,webm @ 0x5f0d178af400] Could not find codec parameters for stream 2 (Subtitle: hdmv_pgs_subtitle (pgssub)): unspecified size
            Consider increasing the value for the 'analyzeduration' (0) and 'probesize' (5000000) options
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle (pgssub)
        """

    @Test
    fun `probes further when a subtitle was not found in the default probe window`() {
        val command = CommandCreator(FakeWrapper(sourceWithLateSubtitle)).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -analyzeduration 600M -probesize 5G -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy -crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `probes further on every seeking input of a trimmed file`() {
        val command = CommandCreator(FakeWrapper(sourceWithLateSubtitle))
            .doAction(arrayOf("somefile.mkv", "-trimStart=28"))

        assertEquals(
            "ffmpeg -n -analyzeduration 600M -probesize 5G -ss 28 -i somefile.mkv " +
                "-analyzeduration 600M -probesize 5G -ss 28 -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 copy " +
                "-map 1:s:0 -c:s:0 copy -crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-copypriorss 0 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `probes further on source inputs of both two-pass commands but not on the encoded video`() {
        val command = CommandCreator(FakeWrapper(sourceWithLateSubtitle))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -analyzeduration 600M -probesize 5G -i somefile.mkv $videoEncoding -crf 17 -preset 2 " +
                "-f matroska Output/somefile_video.mkv" +
                "\n" +
                "ffmpeg -n -analyzeduration 600M -probesize 5G -i somefile.mkv -i Output/somefile_video.mkv " +
                "-map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy -map 0:s:0 -c:s:0 copy " +
                "-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `keeps the default probe window for a stream that probing cannot resolve`() {
        val source = """
            [in#0/matroska,webm @ 0x5f0d178af400] Could not find codec parameters for stream 2 (Attachment: none): unknown codec
            Consider increasing the value for the 'analyzeduration' (0) and 'probesize' (5000000) options
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2: Attachment: none
        """

        val command = CommandCreator(FakeWrapper(source)).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 copy -map 0:t -c:t copy " +
                "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }
}
