import java.util.regex.Pattern

data class Stream(
    val index: Int,
    val lang: String,
    val type: String,
    val codec: String
) {
    companion object {
        val patternWithLang: Pattern = Pattern
            .compile("""Stream #0:(?<index>\d+)(\[(.*?)\])?\((?<lang>\w+)\): (?<type>\w+): (?<codec>.*)""")
        val patternWithoutLang: Pattern = Pattern
            .compile("""Stream #0:(?<index>\d+)(\[(.*?)\])?: (?<type>\w+): (?<codec>.*)""")

        fun from(raw: String, parsedArgs: Map<String, String>): Stream? {
            with(
                patternWithLang
                    .matcher(raw)
                    .apply {
                        if (!matches()) {
                            with(
                                patternWithoutLang
                                    .matcher(raw)
                                    .apply {
                                        if (!matches() || !setOf("Video", "Attachment").contains(group("type"))) {
                                            if (!ignoreMissingLanguage(group("type"), parsedArgs)) {
                                                return null
                                            }
                                        }
                                    }
                            ) {
                                return Stream(
                                    index = group("index").toInt(),
                                    lang = "???",
                                    type = group("type"),
                                    codec = group("codec")
                                )
                            }
                        }
                    }
            ) {
                return Stream(
                    index = group("index").toInt(),
                    lang = group("lang"),
                    type = group("type"),
                    codec = group("codec")
                )
            }
        }

        private fun ignoreMissingLanguage(type: String?, parsedArgs: Map<String, String>) = when (type) {
            "Audio" -> parsedArgs.contains(SET_AUDIO_LANGUAGES)

            "Subtitle" -> parsedArgs.contains(IGNORE_MISSING_SUBTITLE_LANGUAGE)

            else -> true
        }
    }
}
