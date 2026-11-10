// Renders the final mux of -twoPassTranscode as mkvmerge arguments. File 0 is the intermediate file with the encoded
// video (track 0) followed by the AC3 encodes in selection order; file 1 is the source, from which the copied audio,
// the subtitles, chapters, attachments and global tags are taken. Source track IDs are the ffmpeg stream indexes.
// Copied audio tracks get their title here; the AC3 encodes already carry theirs from pass 1.
class MkvmergeArguments {

    fun render(
        audio: List<Mapping>,
        subtitles: List<Mapping>,
        intermediateFile: String,
        sourceFile: String,
        outputFile: String
    ): String {
        val copiedAudio = audio.filter { it.action == COPY }

        val audioOptions = if (copiedAudio.isEmpty()) {
            "-A"
        } else {
            "-a ${copiedAudio.joinToString(",") { "${it.streamIndex}" }}"
        }

        val trackOptions = copiedAudio.flatMap {
            listOfNotNull(
                it.language?.let { lang -> "--language ${it.streamIndex}:$lang" },
                it.title?.let { title -> "--track-name ${it.streamIndex}:\"$title\"" }
            )
        }

        val subtitleOptions = if (subtitles.isEmpty()) {
            "-S"
        } else {
            "-s ${subtitles.joinToString(",") { "${it.streamIndex}" }}"
        }

        var encodedTracks = 0
        val audioOrder = audio.map {
            if (it.action == COPY) {
                "1:${it.streamIndex}"
            } else {
                encodedTracks++
                "0:$encodedTracks"
            }
        }
        val trackOrder = listOf("0:0") + audioOrder + subtitles.map { "1:${it.streamIndex}" }

        return (listOf("-o", outputFile, intermediateFile, "-D", audioOptions) + trackOptions +
            listOf(subtitleOptions, sourceFile, "--track-order", trackOrder.joinToString(",")))
            .joinToString(" ")
    }
}
