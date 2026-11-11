// Writes minimal Matroska files for EbmlCueReaderTest: just the elements the reader looks at. Every size is written
// with 8 bytes, so an element's length doesn't depend on its values and SeekHead positions can be computed upfront.
class MatroskaBuilder(
    private val tracks: List<Track>
) {
    data class Track(
        val number: Long,
        val type: Long
    )

    data class Cue(
        val time: Long,
        val track: Long
    )

    fun build(
        cues: List<Cue>?,
        timestampScale: Long? = null,
        cuesBeforeClusters: Boolean = false,
        withSeekHead: Boolean = true
    ): ByteArray {
        val info = element(INFO, *listOfNotNull(timestampScale?.let { uint(TIMESTAMP_SCALE, it) }).toTypedArray())
        val trackEntries = tracks.map { element(TRACK_ENTRY, uint(TRACK_NUMBER, it.number), uint(TRACK_TYPE, it.type)) }
        val tracksElement = element(TRACKS, *trackEntries.toTypedArray())
        val cluster = element(CLUSTER, uint(CLUSTER_TIMESTAMP, 0))
        val cuesElement = cues?.let { list ->
            element(CUES, *list.map {
                element(CUE_POINT, uint(CUE_TIME, it.time), element(CUE_TRACK_POSITIONS, uint(CUE_TRACK, it.track)))
            }.toTypedArray())
        }

        val body = if (cuesBeforeClusters) {
            listOfNotNull(info, tracksElement, cuesElement, cluster)
        } else {
            listOfNotNull(info, tracksElement, cluster, cuesElement)
        }
        val ids = if (cuesBeforeClusters) {
            listOfNotNull(INFO, TRACKS, cuesElement?.let { CUES }, CLUSTER)
        } else {
            listOfNotNull(INFO, TRACKS, CLUSTER, cuesElement?.let { CUES })
        }

        val segmentChildren = if (withSeekHead) {
            val seekHeadSize = seekHead(ids.filter { it != CLUSTER }.map { it to 0L }).size
            var position = seekHeadSize.toLong()
            val positions = ids.zip(body).map { (id, bytes) ->
                val entry = id to position
                position += bytes.size
                entry
            }
            listOf(seekHead(positions.filter { it.first != CLUSTER })) + body
        } else {
            body
        }

        return element(EBML, binary(DOC_TYPE, "matroska".toByteArray())) +
            element(SEGMENT, *segmentChildren.toTypedArray())
    }

    private fun seekHead(entries: List<Pair<Long, Long>>) = element(
        SEEK_HEAD,
        *entries.map { (id, position) ->
            element(SEEK, binary(SEEK_ID, idBytes(id)), uint(SEEK_POSITION, position))
        }.toTypedArray()
    )

    private fun element(id: Long, vararg children: ByteArray): ByteArray {
        val content = children.fold(ByteArray(0)) { acc, bytes -> acc + bytes }

        return idBytes(id) + size(content.size.toLong()) + content
    }

    private fun uint(id: Long, value: Long) = binary(id, ByteArray(8) { (value shr (8 * (7 - it))).toByte() })

    private fun binary(id: Long, value: ByteArray) = idBytes(id) + size(value.size.toLong()) + value

    private fun idBytes(id: Long): ByteArray {
        val length = (0..3).last { (id shr (8 * it)) != 0L } + 1

        return ByteArray(length) { (id shr (8 * (length - 1 - it))).toByte() }
    }

    private fun size(value: Long): ByteArray = byteArrayOf(0x01) + ByteArray(7) { (value shr (8 * (6 - it))).toByte() }

    companion object {
        const val VIDEO = 0x01L
        const val AUDIO = 0x02L
        const val SUBTITLE = 0x11L

        private const val EBML = 0x1A45DFA3L
        private const val DOC_TYPE = 0x4282L
        private const val SEGMENT = 0x18538067L
        private const val SEEK_HEAD = 0x114D9B74L
        private const val SEEK = 0x4DBBL
        private const val SEEK_ID = 0x53ABL
        private const val SEEK_POSITION = 0x53ACL
        private const val INFO = 0x1549A966L
        private const val TIMESTAMP_SCALE = 0x2AD7B1L
        private const val TRACKS = 0x1654AE6BL
        private const val TRACK_ENTRY = 0xAEL
        private const val TRACK_NUMBER = 0xD7L
        private const val TRACK_TYPE = 0x83L
        private const val CLUSTER = 0x1F43B675L
        private const val CLUSTER_TIMESTAMP = 0xE7L
        private const val CUES = 0x1C53BB6BL
        private const val CUE_POINT = 0xBBL
        private const val CUE_TIME = 0xB3L
        private const val CUE_TRACK_POSITIONS = 0xB7L
        private const val CUE_TRACK = 0xF7L
    }
}
