import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwoPassTest {

    private val matroskaInput = "Input #0, matroska,webm, from 'somefile.mkv':"

    private val videoEncoding = "-map 0:v:0 -c:v:0 libsvtav1 -g 240 -keyint_min 240 -pix_fmt yuv420p10le " +
        "-svtav1-params tune=0:enable-qm=1:enable-variance-boost=1"

    private val passOneOptions = "-crf 17 -preset 2 -metadata:s:v:0 FFMPEG_COMMANDS_VERSION=$TOOL_VERSION " +
        "-map_chapters -1 -map_metadata:g -1 -max_muxing_queue_size 9999 -max_interleave_delta 0"

    private val basicSource = """
            $matroskaInput
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """

    private val dtsSource = """
            $matroskaInput
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1(eng): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
            Stream #0:2(deu): Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
            Stream #0:3(eng): Subtitle: hdmv_pgs_subtitle, 1920x1080
            Stream #0:4(deu): Subtitle: subrip
            Stream #0:5: Attachment: ttf
        """

    @Test
    fun `encodes video in first pass and muxes source tracks with mkvmerge`() {
        val command = CommandCreator(FakeWrapper(basicSource)).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv $videoEncoding $passOneOptions Output/somefile_intermediate.mkv" +
                "\n" +
                "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
                "-D -a 1 -S somefile.mkv --track-order 0:0,1:1",
            command
        )
    }

    @Test
    fun `encodes ac3 tracks in first pass and keeps track order in mkvmerge`() {
        val command = CommandCreator(FakeWrapper(dtsSource)).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv $videoEncoding -map 0:a:1 -c:a:0 ac3 -map 0:a:0 -c:a:1 ac3 " +
                "$passOneOptions Output/somefile_intermediate.mkv" +
                "\n" +
                "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
                "-D -a 2,1 -s 4,3 somefile.mkv --track-order 0:0,1:2,0:1,1:1,0:2,1:4,1:3",
            command
        )
    }

    @Test
    fun `drops subtitles in mkvmerge`() {
        val command = CommandCreator(FakeWrapper(dtsSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-dropSubtitles"))

        assertEquals(
            "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
                "-D -a 2,1 -S somefile.mkv --track-order 0:0,1:2,0:1,1:1,0:2",
            command.lines()[1]
        )
    }

    @Test
    fun `sets audio languages in first pass for ac3 and in mkvmerge for copied tracks`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            $matroskaInput
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
            Stream #0:1: Audio: dts (DTS-HD MA), 48000 Hz, 5.1(side), s32p (24 bit)
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-setAudioLanguages=deu"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv $videoEncoding -map 0:a:0 -c:a:0 ac3 -metadata:s:a:0 language=deu " +
                "$passOneOptions Output/somefile_intermediate.mkv" +
                "\n" +
                "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
                "-D -a 1 --language 1:deu -S somefile.mkv --track-order 0:0,1:1,0:1",
            command
        )
    }

    @Test
    fun `takes no audio from source without audio tracks`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            $matroskaInput
            Stream #0:0(eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080 [SAR 1:1 DAR 16:9], 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc
        """
            )
        ).doAction(arrayOf("somefile.mkv", "-twoPassTranscode"))

        assertEquals(
            "[ ! -e Output/somefile.mkv ] && mkvmerge -o Output/somefile.mkv Output/somefile_intermediate.mkv " +
                "-D -A -S somefile.mkv --track-order 0:0",
            command.lines()[1]
        )
    }

    @Test
    fun `runs mkvmerge in docker`() {
        val command = CommandCreator(FakeWrapper(basicSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg -n -i /config/somefile.mkv $videoEncoding " +
                "$passOneOptions /config/Output/somefile_intermediate.mkv" +
                "\n" +
                "[ ! -e Output/somefile.mkv ] && docker run --rm -it -v \"\$(pwd)\":/config --entrypoint mkvmerge jlesage/mkvtoolnix " +
                "-o /config/Output/somefile.mkv /config/Output/somefile_intermediate.mkv " +
                "-D -a 1 -S /config/somefile.mkv --track-order 0:0,1:1",
            command
        )
    }

    @Test
    fun `creates unstarted mkvmerge container without overwrite guard`() {
        val command = CommandCreator(FakeWrapper(basicSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-docker", "-unstarted"))

        assertEquals(
            "docker create --rm -it -v \"\$(pwd)\":/config --entrypoint mkvmerge jlesage/mkvtoolnix " +
                "-o /config/Output/somefile.mkv /config/Output/somefile_intermediate.mkv " +
                "-D -a 1 -S /config/somefile.mkv --track-order 0:0,1:1",
            command.lines()[1]
        )
    }

    @Test
    fun `applies alias to ffmpeg pass only`() {
        val command = CommandCreator(FakeWrapper(basicSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-alias=ffmpeg9"))

        assertEquals(true, command.lines()[0].startsWith("ffmpeg9 -n -i somefile.mkv "), command)
        assertEquals(true, command.lines()[1].startsWith("[ ! -e Output/somefile.mkv ] && mkvmerge "), command)
    }

    @Test
    fun `applies max interleave delta to first pass`() {
        val command = CommandCreator(FakeWrapper(basicSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-maxInterleaveDelta=5"))

        assertEquals(true, command.lines()[0].endsWith("-max_interleave_delta 5000 Output/somefile_intermediate.mkv"), command)
    }

    @Test
    fun `rejects non matroska source`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Input #0, mpegts, from 'somefile.ts':
            Stream #0:0[0x1011](eng): Video: h264 (High), yuv420p(tv, bt709, progressive), 1920x1080, 23.98 fps, 23.98 tbr, 90k tbn
            Stream #0:1[0x1100](deu): Audio: ac3, 48000 Hz, stereo, fltp, 224 kb/s
        """
            )
        ).doAction(arrayOf("somefile.ts", "-twoPassTranscode"))

        assertEquals("[Error] -twoPassTranscode only supports Matroska sources", command)
    }

    @Test
    fun `rejects trimStart`() {
        val command = CommandCreator(FakeWrapper(basicSource))
            .doAction(arrayOf("somefile.mkv", "-twoPassTranscode", "-trimStart=28"))

        assertEquals("[Error] -trimStart cannot be used with -twoPassTranscode yet", command)
    }
}
