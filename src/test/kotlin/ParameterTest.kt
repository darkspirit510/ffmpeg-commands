import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals

class ParameterTest {

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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 " +
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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:3 -c:a:1 copy " +
                "-map 0:a:2 -c:a:2 ac3 " +
                "-map 0:a:0 -c:a:3 copy " +
                "-map 0:a:1 -c:a:4 copy " +
                "-map 0:a:0 -c:a:5 ac3 " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:3 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:a:2 -c:a:3 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:3 -c:a:1 copy " +
                "-map 0:a:2 -c:a:2 ac3 " +
                "-map 0:a:4 -c:a:3 copy " +
                "-map 0:a:0 -c:a:4 copy " +
                "-map 0:a:1 -c:a:5 copy " +
                "-map 0:a:0 -c:a:6 ac3 " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:5 -c:s:1 copy " +
                "-map 0:s:0 -c:s:2 copy " +
                "-map 0:s:4 -c:s:3 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores additional video stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0: Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1: Video: png (MPNG / 0xDEADBEEF), none(pc), 123x456, SAR 1:1 DAR 123:456, 25 fps, 25 tbr, 1k tbn
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:1 -c:a:0 copy " +
                "-map 0:a:2 -c:a:1 copy " +
                "-map 0:a:0 -c:a:2 copy " +
                "-map 0:s:1 -c:s:0 copy " +
                "-map 0:s:2 -c:s:1 copy " +
                "-map 0:s:0 -c:s:2 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `copies AV1 video stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: av1 (libdav1d) (Main), yuv420p(tv, bt709, progressive), 1920x1080, SAR 1:1 DAR 16:9, 23.98 fps, 23.98 tbr, 1k tbn
            Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:a:1 -c:a:3 ac3 " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 copy " +
                "-map 0:a:1 -c:a:2 copy " +
                "-map 0:s:2 -c:s:0 copy " +
                "-map 0:s:0 -c:s:1 copy " +
                "-map 0:s:1 -c:s:2 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-map 0:t -c:t copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `ignores stream identifier in square brackets`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0[0x1](und): Video: h264 (Main) (avc1 / 0x31637661), yuv420p(tv, bt709, progressive), 1280x720, 2800 kb/s, 23.98 fps, 23.98 tbr, 90k tbn (default)
            Stream #0:1[0x2](deu): Audio: aac (LC) (mp4a / 0x6134706D), 48000 Hz, 5.1, fltp, 657 kb/s (default)
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:a:0 -c:a:1 ac3 " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `drops subtitles via option`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-dropSubtitles"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `sets single language for missing audio language`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0: Video: hevc (Main), yuv420p(tv), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, start 0.088000 (default)
                Stream #0:1: Audio: mp3 (mp3float), 48000 Hz, stereo, fltp, 80 kb/s (default)
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-setAudioLanguages=ger"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-metadata:s:a:0 language=ger " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `sets multiple language for missing audio language`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0: Video: hevc (Main), yuv420p(tv), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, start 0.088000 (default)
                Stream #0:1: Audio: ac3, 48000 Hz, stereo, fltp, 448 kb/s (default)
                Stream #0:2: Audio: ac3, 48000 Hz, stereo, fltp, 448 kb/s (default)
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-setAudioLanguages=ger,eng"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:a:1 -c:a:1 copy " +
                "-metadata:s:a:0 language=ger " +
                "-metadata:s:a:1 language=eng " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `sets multiple language for missing audio language when some audio channels have languages`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0: Video: hevc (Main), yuv420p(tv), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, start 0.088000 (default)
                Stream #0:1(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                Stream #0:2: Audio: ac3, 48000 Hz, stereo, fltp, 448 kb/s (default)
                Stream #0:3(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                Stream #0:4: Audio: ac3, 48000 Hz, stereo, fltp, 448 kb/s (default)
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-setAudioLanguages=ger,eng"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:a:1 -c:a:1 copy " +
                "-map 0:a:2 -c:a:2 copy " +
                "-map 0:a:3 -c:a:3 copy " +
                "-metadata:s:a:1 language=ger " +
                "-metadata:s:a:3 language=eng " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `sets language for mp3 audio stream without conversion`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0: Video: hevc (Main), yuv420p(tv), 640x464 [SAR 1:1 DAR 40:29], 23.98 fps, 23.98 tbr, 1k tbn, start 0.042000 (default)
                Stream #0:1: Audio: mp3 (mp3float), 48000 Hz, stereo, fltp, 80 kb/s (default)
            """
            )
        ).doAction(arrayOf("video.mkv", "-setAudioLanguages=ger"))

        assertEquals(
            "ffmpeg -n -i video.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-metadata:s:a:0 language=ger " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/video.mkv",
            command
        )
    }

    @Test
    fun `uses custom maxInterleaveDelta value`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-maxInterleaveDelta=50"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 50000 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `uses zero maxInterleaveDelta value`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-maxInterleaveDelta=0"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `fails on negative maxInterleaveDelta value`() {
        assertEquals(
            "[Error] maxInterleaveDelta must be non-negative",
            CommandCreator(
                FakeWrapper(
                    """
                    Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                    Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                """
                )
            ).doAction(arrayOf("somefile.mkv", "-maxInterleaveDelta=-1"))
        )
    }

    @Test
    fun `fails on non-integer maxInterleaveDelta value`() {
        assertEquals(
            "[Error] maxInterleaveDelta must be an integer",
            CommandCreator(
                FakeWrapper(
                    """
                    Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                    Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                """
                )
            ).doAction(arrayOf("somefile.mkv", "-maxInterleaveDelta=abc"))
        )
    }

    @Test
    fun `fails on empty maxInterleaveDelta value`() {
        assertEquals(
            "[Error] maxInterleaveDelta cannot be empty",
            CommandCreator(
                FakeWrapper(
                    """
                    Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                    Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                """
                )
            ).doAction(arrayOf("somefile.mkv", "-maxInterleaveDelta="))
        )
    }
}
