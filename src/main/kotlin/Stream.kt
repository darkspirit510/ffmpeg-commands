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
            with(patternWithLang.matcher(raw)) {
                if (matches()) {
                    return Stream(
                        index = group("index").toInt(),
                        lang = group("lang"),
                        type = group("type"),
                        codec = group("codec")
                    )
                }
            }

            with(patternWithoutLang.matcher(raw)) {
                if (!matches()) {
                    return null
                }

                val type = group("type")

                if (!setOf("Video", "Attachment").contains(type) && !ignoreMissingLanguage(type, parsedArgs)) {
                    return null
                }

                return Stream(
                    index = group("index").toInt(),
                    lang = "???",
                    type = type,
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
