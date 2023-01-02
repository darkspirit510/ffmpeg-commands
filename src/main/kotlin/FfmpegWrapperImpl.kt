class FfmpegWrapperImpl: FfmpegWrapper {
    override fun read(name: String): List<String> = with(
        ProcessBuilder("ffmpeg", "-i", name)
            .redirectErrorStream(true)
            .start()
    ) {
        waitFor()
        inputStream.bufferedReader().readLines()
    }
}
