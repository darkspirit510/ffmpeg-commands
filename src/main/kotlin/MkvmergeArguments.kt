// Renders the final mux as mkvmerge arguments. The optional intermediate file (file 0) holds what ffmpeg encoded: the
// video as track 0 followed by the AC3 encodes in selection order, or, when the video is copied from the source, only
// the AC3 encodes. The source (the last file) supplies the copied audio, the subtitles, chapters, attachments and global
// tags, and the video when sourceVideo is set. Source track IDs are the ffmpeg stream indexes.
// Copied tracks get their title here; the AC3 encodes already carry theirs from the ffmpeg pass.
class MkvmergeArguments {

    fun render(
        audio: List<Mapping>,
        subtitles: List<Mapping>,
        intermediateFile: String?,
        sourceVideo: Int?,
        sourceFile: String,
        outputFile: String
    ): String {
        require(intermediateFile != null || (sourceVideo != null && audio.all { it.action == COPY })) {
            "Without an intermediate file, every track has to come from the source"
        }

        val copiedAudio = audio.filter { it.action == COPY }
        val sourceFileIndex = if (intermediateFile == null) {
            0
        } else {
            1
        }

        val videoOption = if (sourceVideo == null) {
            "-D"
        } else {
            "-d $sourceVideo"
        }

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

        val videoOrder = if (sourceVideo == null) {
            "0:0"
        } else {
            "$sourceFileIndex:$sourceVideo"
        }

        var encodedTrack = if (sourceVideo == null) {
            1
        } else {
            0
        }
        val audioOrder = audio.map {
            if (it.action == COPY) {
                "$sourceFileIndex:${it.streamIndex}"
            } else {
                "0:${encodedTrack++}"
            }
        }
        val trackOrder = listOf(videoOrder) + audioOrder + subtitles.map { "$sourceFileIndex:${it.streamIndex}" }

        return (listOf("-o", outputFile) + listOfNotNull(intermediateFile) + videoOption + audioOptions + trackOptions +
            listOf(subtitleOptions, sourceFile, "--track-order", trackOrder.joinToString(",")))
            .joinToString(" ")
    }
}
