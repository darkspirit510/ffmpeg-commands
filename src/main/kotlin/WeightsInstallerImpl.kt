import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal const val WEIGHTS_FILE_NAME = "nnedi3_weights.bin"
private const val WEIGHTS_FILE_SIZE = 13574928L

class WeightsInstallerImpl(
    private val targetDir: File = File(System.getProperty("user.home"), ".cache/ffmpeg-commands"),
    private val stderr: PrintStream = System.err
) : WeightsInstaller {

    override fun install() {
        val weightsFile = File(targetDir, WEIGHTS_FILE_NAME)

        if (weightsFile.length() == WEIGHTS_FILE_SIZE) {
            return
        }

        val resource = javaClass.getResourceAsStream("/$WEIGHTS_FILE_NAME")
            ?: throw IOException("$WEIGHTS_FILE_NAME is missing from the jar")

        Files.createDirectories(targetDir.toPath())

        // Write to a temp file first, so an interrupted copy never leaves a truncated weights file behind.
        val tempFile = Files.createTempFile(targetDir.toPath(), WEIGHTS_FILE_NAME, ".tmp")

        try {
            resource.use { Files.copy(it, tempFile, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(tempFile, weightsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tempFile)
        }

        stderr.println("Extracted nnedi weights to ~/.cache/ffmpeg-commands/")
    }
}
