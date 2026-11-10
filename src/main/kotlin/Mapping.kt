// One selected source track. index counts within its type (as in 0:a:N), streamIndex is the absolute #0:N, which is
// also the mkvmerge track ID in a Matroska source. language replaces a missing language tag (-setAudioLanguages).
data class Mapping(
    val index: Int,
    val streamIndex: Int,
    val codec: String,
    val action: String,
    val language: String?
)
