import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CommandCreatorTest {

    @Test
    fun `returns audio filtered and ordered`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(ita): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s (default)    
            Stream #0:3(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(spa): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:2 -c:a:0 copy -map 0:a:1 -c:a:1 copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `uses mkv as output file type`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mp4"))

        assertEquals(
            "ffmpeg -n -i somefile.mp4 -map 0:v -c:v libx265 " +
                    "-map 0:a:0 -c:a:0 copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `escapes spaces in filename`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("Some File to convert.mkv"))

        assertEquals(
            "ffmpeg -n -i Some\\ File\\ to\\ convert.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:0 -c:a:0 copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/Some\\ File\\ to\\ convert.mkv",
            command
        )
    }

    @Test
    fun `transforms audio on missing ac3 stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
            Stream #0:2(eng): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:3(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit) (default)
            Stream #0:4(deu): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:2 -c:a:0 copy -map 0:a:3 -c:a:1 copy -map 0:a:2 -c:a:2 ac3 " +
                    "-map 0:a:0 -c:a:3 copy -map 0:a:1 -c:a:4 copy -map 0:a:0 -c:a:5 ac3 " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `skips transformation if ac3 stream exists`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
            Stream #0:2(eng): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:3(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:3 -c:a:0 copy -map 0:a:0 -c:a:1 copy -map 0:a:1 -c:a:2 copy -map 0:a:2 -c:a:3 copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `transforms audio to ac3 when existing ac3 stream is (probably) commentary`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
            Stream #0:2(eng): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:3(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit) (default)
            Stream #0:4(deu): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:5(deu): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:2 -c:a:0 copy -map 0:a:3 -c:a:1 copy -map 0:a:2 -c:a:2 ac3 -map 0:a:4 -c:a:3 copy " +
                    "-map 0:a:0 -c:a:4 copy -map 0:a:1 -c:a:5 copy -map 0:a:0 -c:a:6 ac3 " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `returns subtitles filtered and ordered`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:3(spa): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:4(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:5(tur): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:6(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:7(deu): Subtitle: hdmv_pgs_subtitle
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:0 -c:a:0 copy " +
                    "-map 0:s:2 -map 0:s:5 -map 0:s:0 -map 0:s:4 -c:s copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `fails on multiple video streams`() {
        assertThrows<IllegalArgumentException> {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
                )
            ).doAction(arrayOf("somefile.mkv"))
        }
    }

    @Test
    fun `fails on unknown stream type`() {
        assertThrows<IllegalArgumentException> {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(deu): Attachment: something
        """
                )
            ).doAction(arrayOf("somefile.mkv"))
        }
    }

    @Test
    fun `fails on missing language for audio`() {
        assertThrows<IllegalArgumentException> {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1: Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
                )
            ).doAction(arrayOf("somefile.mkv"))
        }
    }

    @Test
    fun `fails on missing language for subtitle`() {
        assertThrows<IllegalArgumentException> {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2: Subtitle: hdmv_pgs_subtitle
        """
                )
            ).doAction(arrayOf("somefile.mkv"))
        }
    }

    @Test
    fun `ignores guessed message`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Guessed Channel Layout for Input Stream #0.1 : stereo
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:0 -c:a:0 copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `handles real world example (1)`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: truehd, 48000 Hz, 5.1(side), s32 (24 bit) (default)
            Stream #0:2(eng): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:3(spa): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:4(spa): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:5(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:6(eng): Audio: eac3, 48000 Hz, stereo, fltp
            Stream #0:7(ita): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:8(por): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:9(por): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:10(deu): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:11(tur): Audio: ac3, 48000 Hz, 5.1(side), fltp, 640 kb/s
            Stream #0:12(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:13(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:14(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:15(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:16(ita): Subtitle: hdmv_pgs_subtitle
            Stream #0:17(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:18(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:19(tur): Subtitle: hdmv_pgs_subtitle
            Stream #0:20(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:21(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:22(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:23(ita): Subtitle: hdmv_pgs_subtitle
            Stream #0:24(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:25(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:26(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:27(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:28(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:29(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:30(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:31(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:32(spa): Subtitle: hdmv_pgs_subtitle
            Stream #0:33(ita): Subtitle: hdmv_pgs_subtitle
            Stream #0:34(ita): Subtitle: hdmv_pgs_subtitle
            Stream #0:35(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:36(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:37(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:38(por): Subtitle: hdmv_pgs_subtitle
            Stream #0:39(eng): Subtitle: hdmv_pgs_subtitle
            Stream #0:40(tur): Subtitle: hdmv_pgs_subtitle
            Stream #0:41(tur): Subtitle: hdmv_pgs_subtitle
    """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                    "-map 0:a:9 -c:a:0 copy -map 0:a:0 -c:a:1 copy -map 0:a:1 -c:a:2 copy " +
                    "-map 0:a:4 -c:a:3 copy -map 0:a:5 -c:a:4 copy " +
                    "-map 0:s:1 -map 0:s:8 -map 0:s:13 -map 0:s:14 -map 0:s:0 -map 0:s:9 " +
                    "-map 0:s:15 -map 0:s:16 -map 0:s:27 -c:s copy " +
                    "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    class FakeWrapper(
        private val returnContent: String
    ) : FfmpegWrapper {
        override fun read(name: String) = returnContent.split("\n")
    }

}
