import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals

class WeightsInstallerImplTest {

    @TempDir
    lateinit var tempDir: File

    private val stderr = ByteArrayOutputStream()

    private val weightsFile
        get() = File(tempDir, "cache/nnedi3_weights.bin")

    private fun installer() = WeightsInstallerImpl(File(tempDir, "cache"), PrintStream(stderr, true))

    @Test
    fun `extracts weights when missing`() {
        installer().install()

        assertEquals(13574928L, weightsFile.length())
    }

    @Test
    fun `leaves intact weights file alone`() {
        weightsFile.parentFile.mkdirs()
        weightsFile.writeBytes(ByteArray(13574928))
        weightsFile.setLastModified(1000000000000L)

        installer().install()

        assertEquals(1000000000000L, weightsFile.lastModified())
        assertEquals("", stderr.toString())
    }

    @Test
    fun `replaces weights file with wrong size`() {
        weightsFile.parentFile.mkdirs()
        weightsFile.writeBytes(ByteArray(3))

        installer().install()

        assertEquals(13574928L, weightsFile.length())
    }

    @Test
    fun `reports extraction on stderr`() {
        installer().install()

        assertEquals("Extracted nnedi weights to ~/.cache/ffmpeg-commands/" + System.lineSeparator(), stderr.toString())
    }
}
