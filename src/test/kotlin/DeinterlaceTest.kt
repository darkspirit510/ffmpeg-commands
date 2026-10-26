import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DeinterlaceTest {

    private val interlacedSource = """
            Stream #0:0(deu): Video: mpeg2video (Main), yuv420p(tv, top first), 720x576 [SAR 64:45 DAR 16:9], 25 fps, 25 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
        """

    @Test
    fun `deinterlaces with nnedi weights from home cache`() {
        val installer = FakeWeightsInstaller()

        val command = CommandCreator(FakeWrapper(interlacedSource), installer)
            .doAction(arrayOf("somefile.mkv", "-deinterlace"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-vf \"nnedi=weights=\$HOME/.cache/ffmpeg-commands/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\" " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
        assertEquals(1, installer.calls)
    }

    @Test
    fun `mounts weights cache into docker container`() {
        val command = CommandCreator(FakeWrapper(interlacedSource), FakeWeightsInstaller())
            .doAction(arrayOf("somefile.mkv", "-deinterlace", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config -v \"\$HOME/.cache/ffmpeg-commands\":/weights linuxserver/ffmpeg " +
                "-n -i /config/somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-vf \"nnedi=weights=/weights/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\" " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `deinterlaces only in video pass of two-pass transcode`() {
        val command = CommandCreator(FakeWrapper(interlacedSource), FakeWeightsInstaller())
            .doAction(arrayOf("somefile.mkv", "-deinterlace", "-twoPassTranscode", "-docker"))

        assertEquals(
            "docker run --rm -it -v \"\$(pwd)\":/config -v \"\$HOME/.cache/ffmpeg-commands\":/weights linuxserver/ffmpeg " +
                "-n -i /config/somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-vf \"nnedi=weights=/weights/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\" " +
                "-crf 17 -preset 2 -f matroska /config/Output/somefile_video.mkv" +
                "\n" +
                "docker run --rm -it -v \"\$(pwd)\":/config linuxserver/ffmpeg " +
                "-n -i /config/somefile.mkv -i /config/Output/somefile_video.mkv -map 1:v:0 -c:v:0 copy " +
                "-map 0:a:0 -c:a:0 copy " +
                "-max_muxing_queue_size 9999 -max_interleave_delta 0 /config/Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `re-encodes av1 source instead of copying`() {
        val command = CommandCreator(
            FakeWrapper(
                """
            Stream #0:0(deu): Video: av1 (libdav1d) (Main), yuv420p10le(tv, top first), 720x576, 25 fps, 25 tbr, 1k tbn
            Stream #0:1(deu): Audio: ac3, 48000 Hz, stereo, fltp, 192 kb/s
        """
            ),
            FakeWeightsInstaller()
        ).doAction(arrayOf("somefile.mkv", "-deinterlace"))

        assertEquals(
            "ffmpeg -n -i somefile.mkv " +
                "-map 0:v:0 -c:v:0 libsvtav1 -g 480 -keyint_min 480 -pix_fmt yuv420p10le -svtav1-params tune=0:enable-qm=1:enable-variance-boost=1 " +
                "-vf \"nnedi=weights=\$HOME/.cache/ffmpeg-commands/nnedi3_weights.bin:field=af:deint=interlaced:nns=n128:qual=slow\" " +
                "-map 0:a:0 -c:a:0 copy " +
                "-crf 17 -preset 2 -max_muxing_queue_size 9999 -max_interleave_delta 0 Output/somefile.mkv",
            command
        )
    }

    @Test
    fun `does not install weights without deinterlace flag`() {
        val installer = FakeWeightsInstaller()

        CommandCreator(FakeWrapper(interlacedSource), installer).doAction(arrayOf("somefile.mkv"))

        assertEquals(0, installer.calls)
    }

    @Test
    fun `returns error when weights cannot be installed`() {
        val command = CommandCreator(FakeWrapper(interlacedSource), FakeWeightsInstaller("disk full"))
            .doAction(arrayOf("somefile.mkv", "-deinterlace"))

        assertEquals("[Error] Could not install nnedi weights: disk full", command)
    }

    @Test
    fun `does not install weights when input file does not exist`() {
        val installer = FakeWeightsInstaller()

        val command = CommandCreator(
            FakeWrapper("somefile.mkv: Error opening input files: No such file or directory"),
            installer
        ).doAction(arrayOf("somefile.mkv", "-deinterlace"))

        assertEquals("[Error] File somefile.mkv does not exist or can't be accessed.", command)
        assertEquals(0, installer.calls)
    }
}
