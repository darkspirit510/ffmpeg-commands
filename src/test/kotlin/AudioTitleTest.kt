import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioTitleTest {

    @Test
    fun `names the channel layout`() {
        val expected = mapOf(
            "mono" to "Mono",
            "stereo" to "Stereo",
            "downmix" to "Stereo",
            "2.1" to "Stereo 2.1",
            "3.0" to "Surround 3.0",
            "3.0(back)" to "Surround 3.0",
            "4.0" to "Surround 4.0",
            "quad" to "Surround 4.0",
            "quad(side)" to "Surround 4.0",
            "3.1" to "Surround 3.1",
            "4.1" to "Surround 4.1",
            "5.0" to "Surround 5.0",
            "5.0(side)" to "Surround 5.0",
            "5.1" to "Surround 5.1",
            "5.1(side)" to "Surround 5.1",
            "6.0" to "Surround 6.0",
            "6.0(front)" to "Surround 6.0",
            "hexagonal" to "Surround 6.0",
            "6.1" to "Surround 6.1",
            "6.1(back)" to "Surround 6.1",
            "6.1(front)" to "Surround 6.1",
            "7.0" to "Surround 7.0",
            "7.0(front)" to "Surround 7.0",
            "7.1" to "Surround 7.1",
            "7.1(wide)" to "Surround 7.1",
            "7.1(wide-side)" to "Surround 7.1",
            "octagonal" to "Surround 8.0"
        )

        expected.forEach { (layout, title) ->
            assertEquals("$title (AC3)", AudioTitle.forStream("ac3, 48000 Hz, $layout, fltp, 640 kb/s"), layout)
        }
    }

    @Test
    fun `names the codec`() {
        val expected = mapOf(
            "ac3" to "AC3",
            "eac3" to "E-AC3",
            "eac3 (Dolby Digital Plus + Dolby Atmos)" to "E-AC3 Atmos",
            "truehd" to "TrueHD",
            "truehd (Dolby TrueHD + Dolby Atmos)" to "TrueHD Atmos",
            "dts (DTS)" to "DTS",
            "dts (DTS-ES)" to "DTS",
            "dts (DTS 96/24)" to "DTS",
            "dts (DTS-HD MA)" to "DTS-HD MA",
            "dts (DTS-HD MA + DTS:X)" to "DTS:X",
            "dts (DTS-HD HRA)" to "DTS-HD HRA",
            "dts (dca) (DTS)" to "DTS",
            "dts (dca) (DTS-HD MA)" to "DTS-HD MA",
            "dts (dca) (DTS-HD MA + DTS:X)" to "DTS:X",
            "truehd (truehd) (Dolby TrueHD + Dolby Atmos)" to "TrueHD Atmos",
            "aac (LC)" to "AAC",
            "aac (HE-AAC) (mp4a / 0x6134706D)" to "AAC",
            "flac" to "FLAC",
            "opus" to "Opus",
            "vorbis" to "Vorbis",
            "mp3" to "MP3",
            "mp3 (mp3float)" to "MP3",
            "mp2" to "MP2",
            "pcm_s16le" to "PCM",
            "pcm_s24le" to "PCM"
        )

        expected.forEach { (codec, name) ->
            assertEquals("Stereo ($name)", AudioTitle.forStream("$codec, 48000 Hz, stereo, fltp"), codec)
        }
    }

    @Test
    fun `names a stream without channel layout by the layout ffmpeg assumes for its channel count`() {
        val expected = mapOf(
            "1 channels" to "Mono",
            "2 channels" to "Stereo",
            "3 channels" to "Stereo 2.1",
            "4 channels" to "Surround 4.0",
            "5 channels" to "Surround 5.0",
            "6 channels" to "Surround 5.1",
            "7 channels" to "Surround 6.1",
            "8 channels" to "Surround 7.1"
        )

        expected.forEach { (layout, title) ->
            assertEquals("$title (PCM)", AudioTitle.forStream("pcm_s16le, 48000 Hz, $layout, s16, 4608 kb/s"), layout)
        }
    }

    @Test
    fun `rejects an unknown channel layout`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AudioTitle.forStream("ac3, 48000 Hz, 5.1.4, fltp, 640 kb/s")
        }

        assertEquals("unknown channel layout '5.1.4'", error.message)
    }

    @Test
    fun `rejects an unknown channel count`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AudioTitle.forStream("pcm_s16le, 48000 Hz, 12 channels, s16, 9216 kb/s")
        }

        assertEquals("unknown channel layout '12 channels'", error.message)
    }

    @Test
    fun `rejects an unknown codec`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AudioTitle.forStream("dts (DTS Express), 48000 Hz, stereo, fltp")
        }

        assertEquals("unknown audio codec 'dts (DTS Express)'", error.message)
    }

    @Test
    fun `keeps a layout the AC3 encoder supports for the AC3 copy`() {
        listOf("stereo" to "Stereo", "5.1(side)" to "Surround 5.1", "4.1" to "Surround 4.1").forEach { (layout, title) ->
            val codec = "dts (DTS), 48000 Hz, $layout, fltp, 1536 kb/s"

            assertEquals("$title (AC3)", AudioTitle.forAc3Copy(codec), layout)
            assertFalse(AudioTitle.needsAc3Downmix(codec), layout)
        }
    }

    @Test
    fun `downmixes a layout the AC3 encoder does not support to 5_1 for the AC3 copy`() {
        listOf("6.0", "hexagonal", "6.1", "7.0", "7.1", "7.1(wide)", "octagonal", "7 channels", "8 channels").forEach { layout ->
            val codec = "truehd (Dolby TrueHD + Dolby Atmos), 48000 Hz, $layout, s32 (24 bit)"

            assertEquals("Surround 5.1 (AC3)", AudioTitle.forAc3Copy(codec), layout)
            assertTrue(AudioTitle.needsAc3Downmix(codec), layout)
        }
    }
}
