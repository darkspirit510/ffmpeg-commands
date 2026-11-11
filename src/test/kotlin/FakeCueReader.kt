class FakeCueReader(
    private val cues: Map<Int, List<Double>>?
) : CueReader {
    val requestedFiles = mutableListOf<String>()

    override fun subtitleCueTimes(file: String): Map<Int, List<Double>>? {
        requestedFiles.add(file)

        return cues
    }
}
