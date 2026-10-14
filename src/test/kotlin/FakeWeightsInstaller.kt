import java.io.IOException

class FakeWeightsInstaller(
    private val failure: String? = null
) : WeightsInstaller {
    var calls = 0

    override fun install() {
        calls++

        if (failure != null) {
            throw IOException(failure)
        }
    }
}
