// Decides which audio and subtitle tracks of the source end up in the output, in which order, and which audio track
// is transcoded to AC3. FfmpegTrackArguments and MkvmergeArguments render the same selection.
class TrackSelection(
    private val streams: Map<String, List<Stream>>,
    private val takeLanguages: List<String>,
    private val parsedArgs: Map<String, String>
) {

    fun audio(): List<Mapping> {
        val audioStreams = streams["Audio"] ?: emptyList()
        val setAudioLangs = parsedArgs[SET_AUDIO_LANGUAGES]?.split(",") ?: emptyList()

        val noLangAssignments = mutableMapOf<Int, String>()

        if (setAudioLangs.isNotEmpty()) {
            var langIdx = 0
            audioStreams.forEachIndexed { idx, stream ->
                if (stream.lang == "???" && langIdx < setAudioLangs.size) {
                    noLangAssignments[idx] = setAudioLangs[langIdx]
                    langIdx++
                }
            }
        }

        return takeLanguages
            .flatMap { lang ->
                audioMappingsFor(audioStreams.mapIndexedNotNull { idx, stream ->
                    if (stream.lang == lang ||
                        (stream.lang == "???" && noLangAssignments[idx] == lang)
                    ) {
                        Mapping(
                            index = idx,
                            streamIndex = stream.index,
                            codec = stream.codec,
                            action = COPY,
                            language = noLangAssignments[idx],
                            title = null
                        )
                    } else {
                        null
                    }
                })
            }
            .distinct()
            .map { it.copy(title = title(it)) }
    }

    fun subtitles(): List<Mapping> {
        if (parsedArgs.contains(DROP_SUBTITLES)) {
            return emptyList()
        }

        return takeLanguages.flatMap { lang ->
            (streams["Subtitle"] ?: emptyList()).mapIndexedNotNull { idx, stream ->
                if (stream.lang == lang) {
                    Mapping(
                        index = idx,
                        streamIndex = stream.index,
                        codec = stream.codec,
                        action = COPY,
                        language = null,
                        title = null
                    )
                } else {
                    null
                }
            }
        }
    }

    // Throws for an unknown layout or codec, so the file can be inspected; dropped streams are never checked.
    private fun title(mapping: Mapping): String {
        try {
            if (mapping.action == COPY) {
                return AudioTitle.forStream(mapping.codec)
            }

            return AudioTitle.forAc3Copy(mapping.codec)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Audio stream #0:${mapping.streamIndex}: ${e.message} (${mapping.codec.trim()})")
        }
    }

    private fun audioMappingsFor(sourceMappings: List<Mapping>): List<Mapping> {
        val audioMappings = sourceMappings.toMutableList()

        if (audioMappings.any { !it.codec.startsWith(AC3) } && audioMappings.none {
                it.codec.startsWith(AC3)
                    && !it.codec.contains("stereo, fltp, 192 kb/s")
            }) {
            val lastNonAC3Index = audioMappings
                .indexOf(audioMappings.last { !it.codec.startsWith(AC3) })
            val transcodeSource = audioMappings.first { !it.codec.startsWith(AC3) }
            audioMappings.add(lastNonAC3Index + 1, transcodeSource.copy(action = AC3))
        }

        return audioMappings
    }
}
