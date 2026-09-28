import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:2 -c:a:0 copy " +
                "-map 0:a:1 -c:a:1 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
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
        assertEquals(
            "[Error] Missing language for audio stream. Use -setAudioLanguages parameter to specify languages for audio streams without language tags.",
            CommandCreator(
                FakeWrapper(
                    """
                    Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                    Stream #0:1: Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
                """
                )
            ).doAction(arrayOf("somefile.mkv"))
        )
    }

    @Test
    fun `fails on missing language for subtitle`() {
        assertEquals(
            "[Error] Missing language for subtitle stream",
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            Stream #0:2: Subtitle: hdmv_pgs_subtitle
        """
                )
            ).doAction(arrayOf("somefile.mkv"))
        )
    }

    @Test
    fun `takes alias via option`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.mp4", "-alias=somealias"))

        assertEquals(
            "somealias -n -i somefile.mp4 " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `uses docker wrapper when docker option is set`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `uses docker wrapper with escaped filename`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("Some File`s (2024).mkv", "-docker"))

        assertEquals(
            """docker run --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/Some\ File\`s\ \(2024\).mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 """ +
                """-map 0:a:0 -c:a:0 copy """ +
                """-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/Some\ File\`s\ \(2024\).mkv""",
            command
        )
    }

    @Test
    fun `uses docker wrapper with directory containing spaces`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("My Videos/file.mkv", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/file.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/file.mkv",
            command
        )
    }

    @Test
    fun `uses docker wrapper with directory containing spaces and special chars`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("My & Videos (2024)/file`s.mkv", "-docker"))

        assertEquals(
            """docker run --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/file\`s.mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 """ +
                """-map 0:a:0 -c:a:0 copy """ +
                """-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/file\`s.mkv""",
            command
        )
    }

    @Test
    fun `uses docker wrapper with unstarted and directory containing spaces`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("My Videos/file.mkv", "-docker", "-unstarted"))

        assertEquals(
            """docker create --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/file.mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 """ +
                """-map 0:a:0 -c:a:0 copy """ +
                """-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/file.mkv""",
            command
        )
    }

    @Test
    fun `rejects combination of parameters alias and docker`() {
        assertEquals(
            "[Error] Cannot use alias and docker options together",
            CommandCreator(FakeWrapper(""))
                .doAction(arrayOf("somefile.mkv", "-docker", "-alias=customffmpeg"))
        )
    }

    @Test
    fun `rejects unstarted parameter without docker`() {
        assertEquals(
            "[Error] -unstarted parameter can only be used with -docker",
            CommandCreator(FakeWrapper(""))
                .doAction(arrayOf("somefile.mkv", "-unstarted"))
        )
    }

    @Test
    fun `creates unstarted docker container when unstarted parameter is set`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-docker", "-unstarted"))

        assertEquals(
            "docker create --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `creates unstarted docker container with escaped filename when unstarted parameter is set`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("Test File (1).mkv", "-docker", "-unstarted"))

        assertEquals(
            """docker create --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/Test\ File\ \(1\).mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 """ +
                """-map 0:a:0 -c:a:0 copy """ +
                """-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/Test\ File\ \(1\).mkv""",
            command
        )
    }

    @Test
    fun `escapes shell metacharacters in filename`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("""Tom & Jerry; it's a|b<c>d*e[f]{g}#h~i\j"k.mkv"""))

        assertEquals(
            """ffmpeg -n -i Tom\ \&\ Jerry\;\ it\'s\ a\|b\<c\>d\*e\[f\]\{g\}\#h\~i\\j\"k.mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 """ +
                """-map 0:a:0 -c:a:0 copy """ +
                """-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 """ +
                """Output/Tom\ \&\ Jerry\;\ it\'s\ a\|b\<c\>d\*e\[f\]\{g\}\#h\~i\\j\"k.mkv""",
            command
        )
    }

    @Test
    fun `does not escape unicode letters in filename`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("Amélie.mkv"))

        assertEquals(
            "ffmpeg -n -i Amélie.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/Amélie.mkv",
            command
        )
    }

    @Test
    fun `creates unstarted containers for both commands in two pass mode`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-docker", "-unstarted", "-twoPassTranscode"))

        assertEquals(
            """docker create --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/somefile.mkv """ +
                """-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -crf 17 -preset 2 -f matroska /config/Output/somefile_video.mkv""" +
                "\n" +
                """docker create --rm -it -v "$(pwd)":/config linuxserver/ffmpeg -n -i /config/somefile.mkv -i /config/Output/somefile_video.mkv """ +
                """-map 1:v:0 -c:v:0 copy -map 0:a:0 -c:a:0 copy """ +
                """-max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv""",
            command
        )
    }

    @Test
    fun `runs both commands in two pass docker mode by default`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-docker", "-twoPassTranscode"))

        assertTrue(command.lines().all { it.startsWith("docker run --rm -it ") })
        assertEquals(2, command.lines().size)
    }

    @Test
    fun `omits encoder quality options when video is copied`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: av1 (Main), yuv420p10le(tv, bt709), 1920x1080, 23.98 fps, 23.98 tbr, 1k tbn
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `creates single command in two pass mode when video is copied`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Video: av1 (Main), yuv420p10le(tv, bt709), 1920x1080, 23.98 fps, 23.98 tbr, 1k tbn
                Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `fails on unknown option`() {
        assertEquals(
            "[Error] Unknown option someOption",
            CommandCreator(
                FakeWrapper(
                    """
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:2(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
                )
            ).doAction(arrayOf("somefile.mkv", "-someOption=someValue"))
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
                "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 " +
                "-map 0:a:0 -c:a:0 copy " +
                "-map 0:s:0 -c:s:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `shows error message when given file does not exist`() {
        val result = CommandCreator(
            FakeWrapper(
                """
                Lorem Ipsum Dolor.
                Error opening input files: No such file or directory
        """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals("[Error] File somefile.mkv does not exist or can't be accessed.", result)
    }

    @Test
    fun `shows error message with usage info when parameter is missing`() {
        val result = CommandCreator(
            FakeWrapper("")
        ).doAction(emptyArray())

        assertEquals(
            "[Error] Missing parameter filename. Usage: java -jar ffmpeg-commands.jar filename.mkv [-additionalParameters]",
            result
        )
    }

    @Test
    fun `ignores unparsable lines that merely contain the word Stream`() {
        val command = CommandCreator(
            FakeWrapper(
                """
                title           : My Stream Movie
                Stream groups:
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
    fun `shows error message when file has no video stream`() {
        val result = CommandCreator(
            FakeWrapper(
                """
                Stream #0:0(eng): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
            """
            )
        ).doAction(arrayOf("somefile.mka"))

        assertEquals("[Error] No video stream found in somefile.mka.", result)
    }

    @Test
    fun `shows error message when ffmpeg output has no streams at all`() {
        val result = CommandCreator(
            FakeWrapper(
                """
                somefile.mkv: Invalid data found when processing input
            """
            )
        ).doAction(arrayOf("somefile.mkv"))

        assertEquals("[Error] No video stream found in somefile.mkv.", result)
    }

    @Test
    fun `shows error message when ffmpeg cannot be started`() {
        val wrapper = object : FfmpegWrapper {
            override fun read(name: String): List<String> =
                throw IOException("Cannot run program \"ffmpeg\": error=2, No such file or directory")
        }

        val result = CommandCreator(wrapper).doAction(arrayOf("somefile.mkv"))

        assertEquals(
            "[Error] Could not run ffmpeg: Cannot run program \"ffmpeg\": error=2, No such file or directory",
            result
        )
    }

    private val jarPath = System.getProperty("jar.path")

    private fun ffmpegIsInstalled() = try {
        ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
        true
    } catch (e: IOException) {
        false
    }

    @Test
    fun `main function exits with code 1 when parameter is missing`() {
        val process = ProcessBuilder("java", "-jar", jarPath)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().readText()

        assertEquals(1, exitCode)
        assertTrue(output.contains("[Error] Missing parameter filename"))
    }

    @Test
    fun `main function exits with code 1 when file does not exist`() {
        assumeTrue(ffmpegIsInstalled(), "ffmpeg is not installed")

        val process = ProcessBuilder("java", "-jar", jarPath, "nonexistent.mkv")
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().readText()

        assertEquals(1, exitCode)
        assertTrue(output.contains("[Error] File nonexistent.mkv does not exist"))
    }
}
