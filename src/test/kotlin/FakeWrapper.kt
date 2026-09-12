class FakeWrapper(
    private val returnContent: String
) : FfmpegWrapper {
    override fun read(name: String) = returnContent.split("\n")
}
