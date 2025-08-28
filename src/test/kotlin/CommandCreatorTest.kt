import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:1 -c:a:1 copy " +
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
            "ffmpeg -n -i somefile.mp4 " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `escapes characters in filename`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("Some File`s to convert (1234)?!.mkv"))

        assertEquals(
            "ffmpeg -n -i Some\\ File\\`s\\ to\\ convert\\ \\(1234\\)\\?\\!.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 " +
                "Output/Some\\ File\\`s\\ to\\ convert\\ \\(1234\\)\\?\\!.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:3 -c:a:1 copy " +
                "-map 0:a:2 -c:a:2 ac3 " +
                "-map 0:a:0 -c:a:3 copy " +
                "-map 0:a:1 -c:a:4 copy " +
                "-map 0:a:0 -c:a:5 ac3 " +
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
                "-map 0:a:3 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:a:2 -c:a:3 copy " +
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
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:3 -c:a:1 copy " +
                "-map 0:a:2 -c:a:2 ac3 " +
                "-map 0:a:4 -c:a:3 copy " +
                "-map 0:a:0 -c:a:4 copy " +
                "-map 0:a:1 -c:a:5 copy " +
                "-map 0:a:0 -c:a:6 ac3 " +
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:5 -c:s:1 copy " +
                "-map 0:s:0 -c:s:2 copy " +
                "-map 0:s:4 -c:s:3 copy " +
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
    fun `accepts missing language for video`() {
        assertDoesNotThrow {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0: Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
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
    fun `uses both ger and deu as german language tag`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:3(ger): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:5(deu): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:6(ger): Subtitle: hdmv_pgs_subtitle, 1920x1080
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                "-map 0:a:1 -c:a:0 copy " +
                "-map 0:a:2 -c:a:1 copy " +
                "-map 0:a:0 -c:a:2 copy " +
                "-map 0:s:1 -c:s:0 copy " +
                "-map 0:s:2 -c:s:1 copy " +
                "-map 0:s:0 -c:s:2 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `copies hevc video stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: hevc (Main 10), yuv420p10le(tv, bt2020nc/bt2020/smpte2084), 3840x2160 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `takes additional languages via parameter`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(jap): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:3(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(spa): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:5(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:6(jap): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:7(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:8(spa): Subtitle: hdmv_pgs_subtitle
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-additionalLanguages=jap"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `takes and transforms additional languages via parameter`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(jap): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:3(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(spa): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:5(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:6(jap): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:7(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:8(spa): Subtitle: hdmv_pgs_subtitle
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-additionalLanguages=jap"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:a:1 -c:a:3 ac3 " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores unknown or duplicate languages`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(jap): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:3(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:4(spa): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:5(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:6(jap): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:7(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:8(spa): Subtitle: hdmv_pgs_subtitle
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-additionalLanguages=deu,jap,jap,tur"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `maps attachments`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:3: Attachment: ttf
            """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv -map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-map 0:t -c:t copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `fails on unknown option`() {
        val exception = assertThrows<IllegalArgumentException> {
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
                )
            ).doAction(arrayOf("somefile.mkv", "-someOption=someValue"))
        }

        assertEquals("Unknown option someOption", exception.message)
    }

    @Test
    fun `ignores missing audio language streams via option`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2: Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:3(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-ignoreMissingAudioLanguage"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores missing subtitle language streams via option`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:3: Subtitle: hdmv_pgs_subtitle
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-ignoreMissingSubtitleLanguage"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores missing audio language and copies it anyway via option`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1: Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-preserveMissingAudioLanguage"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores missing audio language and copies it anyway via option, transforms audio on missing ac3 stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1: Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-preserveMissingAudioLanguage"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 ac3 " +
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:9 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:a:4 -c:a:3 copy " +
                "-map 0:a:5 -c:a:4 copy " +
                "-map 0:s:1 -c:s:0 copy " +
                "-map 0:s:8 -c:s:1 copy " +
                "-map 0:s:13 -c:s:2 copy " +
                "-map 0:s:14 -c:s:3 copy " +
                "-map 0:s:0 -c:s:4 copy " +
                "-map 0:s:9 -c:s:5 copy " +
                "-map 0:s:15 -c:s:6 copy " +
                "-map 0:s:16 -c:s:7 copy " +
                "-map 0:s:27 -c:s:8 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `handles real world example (2)`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s16p (default)
            Stream #0:2(deu): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:3(eng): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s16p
            Stream #0:4(eng): Audio: dts (DTS), 48000 Hz, 5.1(side), fltp, 1536 kb/s
            Stream #0:5(deu): Audio: dts (DTS), 48000 Hz, stereo, fltp, 768 kb/s
            Stream #0:6(eng): Audio: dts (DTS), 48000 Hz, stereo, fltp, 768 kb/s
            Stream #0:7(deu): Subtitle: hdmv_pgs_subtitle
            Stream #0:8(deu): Subtitle: hdmv_pgs_subtitle (default)
            Stream #0:9(nld): Subtitle: hdmv_pgs_subtitle
            Stream #0:10(nld): Subtitle: hdmv_pgs_subtitle
    """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:a:1 -c:a:1 copy " +
                "-map 0:a:4 -c:a:2 copy " +
                "-map 0:a:0 -c:a:3 ac3 " +
                "-map 0:a:2 -c:a:4 copy " +
                "-map 0:a:3 -c:a:5 copy " +
                "-map 0:a:5 -c:a:6 copy " +
                "-map 0:a:2 -c:a:7 ac3 " +
                "-map 0:s:0 -c:s:0 copy " +
                "-map 0:s:1 -c:s:1 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `handles real world example (3)`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: mpeg2video (Main), yuv420p(tv, top first), 720x576 [SAR 16:15 DAR 4:3], 25 fps, 25 tbr, 1k tbn, 50 tbc
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s (default)
            Stream #0:2(ger): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
            Stream #0:3(ger): Subtitle: dvd_subtitle, 720x576 (default)
    """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:1 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-crf 17 -preset medium -max_muxing_queue_size 9999 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `handles real world example (4)`() {
        val command = CommandCreator(
            FakeWrapper(
                """
              Stream #0:0(eng): Video: h264 (High), yuv420p(progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn (default)
              Stream #0:1(eng): Audio: aac (LC), 48000 Hz, 5.1, fltp
              Stream #0:2(jpn): Audio: aac (LC), 48000 Hz, stereo, fltp
              Stream #0:3(eng): Subtitle: ass
              Stream #0:4(jpn): Subtitle: ass
              Stream #0:5(ger): Audio: mp3, 48000 Hz, stereo, fltp, 320 kb/s (default)
              Stream #0:6: Attachment: ttf
              Stream #0:7: Attachment: ttf
              Stream #0:8: Attachment: ttf
              Stream #0:9: Attachment: ttf
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-additionalLanguages=jpn"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v -c:v libx265 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:2 -c:a:1 ac3 " +
                "-map 0:a:0 -c:a:2 copy " +
                "-map 0:a:0 -c:a:3 ac3 " +
                "-map 0:a:1 -c:a:4 copy " +
                "-map 0:a:1 -c:a:5 ac3 " +
                "-map 0:s:0 -c:s:0 copy " +
                "-map 0:s:1 -c:s:1 copy " +
                "-map 0:t -c:t copy " +
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
