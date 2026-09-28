class FfmpegWrapperImpl(
    private val executable: String = "ffmpeg"
) : FfmpegWrapper {
    override fun read(name: String): List<String> = with(
        ProcessBuilder(executable, "-i", name)
            .redirectErrorStream(true)
            .start()
    ) {
        // Drain the output before waiting, otherwise a full pipe buffer blocks the process forever.
        val lines = inputStream.bufferedReader().readLines()
        waitFor()
        lines
    }
}
