import MatroskaBuilder.Companion.AUDIO
import MatroskaBuilder.Companion.SUBTITLE
import MatroskaBuilder.Companion.VIDEO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EbmlCueReaderTest {

    @TempDir
    lateinit var tempDir: File

    // Track numbers differ from the position on purpose: the result is keyed by position, i.e. ffmpeg's stream index.
    private val builder = MatroskaBuilder(
        listOf(
            MatroskaBuilder.Track(number = 1, type = VIDEO),
            MatroskaBuilder.Track(number = 2, type = AUDIO),
            MatroskaBuilder.Track(number = 5, type = SUBTITLE),
            MatroskaBuilder.Track(number = 7, type = SUBTITLE)
        )
    )

    private val cues = listOf(
        MatroskaBuilder.Cue(time = 0, track = 1),
        MatroskaBuilder.Cue(time = 56_000, track = 5),
        MatroskaBuilder.Cue(time = 60_000, track = 1),
        MatroskaBuilder.Cue(time = 1_152_500, track = 7),
        MatroskaBuilder.Cue(time = 1_200_000, track = 5)
    )

    private fun write(bytes: ByteArray) = File(tempDir, "movie.mkv").apply { writeBytes(bytes) }.absolutePath

    @Test
    fun `reads subtitle cue times in seconds through the SeekHead, keyed by stream index`() {
        val file = write(builder.build(cues))

        assertEquals(mapOf(2 to listOf(56.0, 1200.0), 3 to listOf(1152.5)), EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `applies the timestamp scale from the segment info`() {
        val file = write(builder.build(cues, timestampScale = 10_000_000))

        assertEquals(mapOf(2 to listOf(560.0, 12000.0), 3 to listOf(11525.0)), EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `finds cues before the first cluster without a SeekHead`() {
        val file = write(builder.build(cues, cuesBeforeClusters = true, withSeekHead = false))

        assertEquals(mapOf(2 to listOf(56.0, 1200.0), 3 to listOf(1152.5)), EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `lists subtitle tracks without cues as empty`() {
        val file = write(builder.build(cues.filter { it.track != 7L }))

        assertEquals(mapOf(2 to listOf(56.0, 1200.0), 3 to emptyList()), EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `returns null for a file without cues`() {
        val file = write(builder.build(null))

        assertNull(EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `returns null for a file that is not Matroska`() {
        val file = write("not a matroska file".toByteArray())

        assertNull(EbmlCueReader().subtitleCueTimes(file))
    }

    @Test
    fun `returns null for a missing file`() {
        assertNull(EbmlCueReader().subtitleCueTimes(File(tempDir, "missing.mkv").absolutePath))
    }
}
