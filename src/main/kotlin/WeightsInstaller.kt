import java.io.IOException

interface WeightsInstaller {
    @Throws(IOException::class)
    fun install()
}
