private const val AC3_DOWNMIX_CHANNELS = 6

// Renders selected tracks as ffmpeg -map/-c options. Output stream indexes follow the order of the given list.
class FfmpegTrackArguments {

    fun audio(mappings: List<Mapping>): String {
        val audioPart = mappings
            .mapIndexed { idx, mapping ->
                "-map 0:a:${mapping.index} -c:a:$idx ${mapping.action}${downmix(mapping, idx)}${title(mapping, idx)}"
            }
            .joinToString(" ")

        val metadataPart = mappings
            .mapIndexedNotNull { idx, mapping ->
                mapping.language?.let { lang -> "-metadata:s:a:$idx language=$lang" }
            }
            .joinToString(" ")

        return listOf(audioPart, metadataPart)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun downmix(mapping: Mapping, outputIndex: Int): String =
        if (mapping.action != COPY && AudioTitle.needsAc3Downmix(mapping.codec)) {
            " -ac:a:$outputIndex $AC3_DOWNMIX_CHANNELS"
        } else {
            ""
        }

    private fun title(mapping: Mapping, outputIndex: Int): String =
        mapping.title?.let { " -metadata:s:a:$outputIndex title=\"$it\"" } ?: ""

    fun subtitles(mappings: List<Mapping>, inputIndex: Int): String = mappings
        .mapIndexed { idx, mapping -> "-map $inputIndex:s:${mapping.index} -c:s:$idx $COPY " }
        .joinToString("")
}
