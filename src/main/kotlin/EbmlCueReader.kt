import java.io.IOException
import java.io.RandomAccessFile

private const val EBML_ID = 0x1A45DFA3L
private const val SEGMENT_ID = 0x18538067L
private const val SEEK_HEAD_ID = 0x114D9B74L
private const val SEEK_ID = 0x4DBBL
private const val SEEK_ID_ID = 0x53ABL
private const val SEEK_POSITION_ID = 0x53ACL
private const val INFO_ID = 0x1549A966L
private const val TIMESTAMP_SCALE_ID = 0x2AD7B1L
private const val TRACKS_ID = 0x1654AE6BL
private const val TRACK_ENTRY_ID = 0xAEL
private const val TRACK_NUMBER_ID = 0xD7L
private const val TRACK_TYPE_ID = 0x83L
private const val CLUSTER_ID = 0x1F43B675L
private const val CUES_ID = 0x1C53BB6BL
private const val CUE_POINT_ID = 0xBBL
private const val CUE_TIME_ID = 0xB3L
private const val CUE_TRACK_POSITIONS_ID = 0xB7L
private const val CUE_TRACK_ID = 0xF7L

private const val SUBTITLE_TRACK_TYPE = 0x11L
private const val DEFAULT_TIMESTAMP_SCALE = 1_000_000L
private const val NANOSECONDS_PER_SECOND = 1e9
// Cues of a feature film are well below 1 MB; anything this large is not what we are looking for.
private const val MAX_ELEMENT_SIZE = 64L * 1024 * 1024
private const val MAX_HEADER_LENGTH = 12

// Reads the Cues of a Matroska file without touching the clusters: the top-level elements before the first cluster,
// then whatever the SeekHead points to (mkvmerge writes the Cues at the end of the file).
class EbmlCueReader : CueReader {

    private class Header(
        val id: Long,
        val dataStart: Long,
        // null for an element of unknown size
        val size: Long?
    )

    private class MalformedFileException(
        message: String
    ) : Exception(message)

    override fun subtitleCueTimes(file: String): Map<Int, List<Double>>? = try {
        RandomAccessFile(file, "r").use { read(it) }
    } catch (e: IOException) {
        null
    } catch (e: MalformedFileException) {
        null
    }

    private fun read(file: RandomAccessFile): Map<Int, List<Double>>? {
        val ebml = header(file, 0)

        if (ebml?.id != EBML_ID || ebml.size == null) {
            return null
        }

        val segment = header(file, ebml.dataStart + ebml.size)

        if (segment?.id != SEGMENT_ID) {
            return null
        }

        val segmentEnd = segment.size?.let { segment.dataStart + it } ?: file.length()
        val elements = topLevelElements(file, segment.dataStart, segmentEnd)

        val cues = elements[CUES_ID] ?: return null
        val tracks = elements[TRACKS_ID] ?: return null
        val timestampScale = elements[INFO_ID]
            ?.let { info -> children(info).firstOrNull { it.first == TIMESTAMP_SCALE_ID }?.let { uint(it.second) } }
            ?: DEFAULT_TIMESTAMP_SCALE

        // ffmpeg numbers the streams of a Matroska file in the order of the track entries.
        val subtitleTracks = children(tracks)
            .filter { it.first == TRACK_ENTRY_ID }
            .mapIndexedNotNull { streamIndex, entry ->
                val fields = children(entry.second)
                val type = fields.firstOrNull { it.first == TRACK_TYPE_ID }?.let { uint(it.second) }
                val number = fields.firstOrNull { it.first == TRACK_NUMBER_ID }?.let { uint(it.second) }

                if (type == SUBTITLE_TRACK_TYPE && number != null) {
                    number to streamIndex
                } else {
                    null
                }
            }
            .toMap()

        val times = subtitleTracks.values.associateWith { mutableListOf<Double>() }

        children(cues).filter { it.first == CUE_POINT_ID }.forEach { cuePoint ->
            val fields = children(cuePoint.second)
            val time = fields.firstOrNull { it.first == CUE_TIME_ID }?.let { uint(it.second) } ?: return@forEach
            val seconds = time.toDouble() * timestampScale / NANOSECONDS_PER_SECOND

            fields.filter { it.first == CUE_TRACK_POSITIONS_ID }
                .mapNotNull { positions -> children(positions.second).firstOrNull { it.first == CUE_TRACK_ID } }
                .mapNotNull { subtitleTracks[uint(it.second)] }
                .distinct()
                .forEach { times.getValue(it).add(seconds) }
        }

        return times.mapValues { it.value.sorted() }
    }

    // Info, Tracks and Cues by ID: first from the elements before the first cluster, the rest through the SeekHeads.
    private fun topLevelElements(file: RandomAccessFile, segmentStart: Long, segmentEnd: Long): Map<Long, ByteArray> {
        val wanted = setOf(INFO_ID, TRACKS_ID, CUES_ID)
        val found = mutableMapOf<Long, ByteArray>()
        val seekHeads = mutableListOf<ByteArray>()
        val visitedSeekHeads = mutableSetOf<Long>()
        var position = segmentStart

        while (position < segmentEnd) {
            val header = header(file, position) ?: break

            if (header.id == CLUSTER_ID || header.size == null) {
                break
            }

            if (header.id == SEEK_HEAD_ID) {
                visitedSeekHeads.add(position)
                seekHeads.add(content(file, header))
            } else if (header.id in wanted && header.id !in found) {
                found[header.id] = content(file, header)
            }

            position = header.dataStart + header.size
        }

        while (seekHeads.isNotEmpty()) {
            seekEntries(seekHeads.removeAt(0)).forEach { (id, relativePosition) ->
                val target = segmentStart + relativePosition

                if (id == SEEK_HEAD_ID && target !in visitedSeekHeads) {
                    visitedSeekHeads.add(target)
                    element(file, target, id)?.let { seekHeads.add(it) }
                } else if (id in wanted && id !in found) {
                    element(file, target, id)?.let { found[id] = it }
                }
            }
        }

        return found
    }

    private fun seekEntries(seekHead: ByteArray): List<Pair<Long, Long>> = children(seekHead)
        .filter { it.first == SEEK_ID }
        .mapNotNull { seek ->
            val fields = children(seek.second)
            val id = fields.firstOrNull { it.first == SEEK_ID_ID }?.let { uint(it.second) }
            val position = fields.firstOrNull { it.first == SEEK_POSITION_ID }?.let { uint(it.second) }

            if (id != null && position != null) {
                id to position
            } else {
                null
            }
        }

    private fun element(file: RandomAccessFile, position: Long, expectedId: Long): ByteArray? {
        val header = header(file, position)

        if (header?.id != expectedId || header.size == null) {
            return null
        }

        return content(file, header)
    }

    private fun header(file: RandomAccessFile, position: Long): Header? {
        if (position < 0 || position >= file.length()) {
            return null
        }

        val bytes = ByteArray(minOf(MAX_HEADER_LENGTH.toLong(), file.length() - position).toInt())
        file.seek(position)
        file.readFully(bytes)

        val (id, idLength) = vint(bytes, 0, keepMarker = true)
        val (size, sizeLength) = size(bytes, idLength)

        return Header(id, position + idLength + sizeLength, size)
    }

    private fun content(file: RandomAccessFile, header: Header): ByteArray {
        val size = header.size ?: throw MalformedFileException("element of unknown size")

        if (size > MAX_ELEMENT_SIZE || header.dataStart + size > file.length()) {
            throw MalformedFileException("element too large or truncated")
        }

        val bytes = ByteArray(size.toInt())
        file.seek(header.dataStart)
        file.readFully(bytes)

        return bytes
    }

    // The child elements of a master element as (ID, content) pairs.
    private fun children(bytes: ByteArray): List<Pair<Long, ByteArray>> {
        val result = mutableListOf<Pair<Long, ByteArray>>()
        var offset = 0

        while (offset < bytes.size) {
            val (id, idLength) = vint(bytes, offset, keepMarker = true)
            val (size, sizeLength) = size(bytes, offset + idLength)
            val start = offset + idLength + sizeLength

            if (size == null || start + size > bytes.size) {
                throw MalformedFileException("child element of unknown size or truncated")
            }

            result.add(id to bytes.copyOfRange(start, start + size.toInt()))
            offset = start + size.toInt()
        }

        return result
    }

    // An element size and its length in bytes; all value bits set means unknown size, returned as null.
    private fun size(bytes: ByteArray, offset: Int): Pair<Long?, Int> {
        val (value, length) = vint(bytes, offset, keepMarker = false)

        return if (value == (1L shl (7 * length)) - 1) {
            null to length
        } else {
            value to length
        }
    }

    // An EBML variable-length integer and its length in bytes. IDs keep the length marker, sizes don't.
    private fun vint(bytes: ByteArray, offset: Int, keepMarker: Boolean): Pair<Long, Int> {
        if (offset >= bytes.size) {
            throw MalformedFileException("unexpected end of data")
        }

        val first = bytes[offset].toInt() and 0xFF
        val length = Integer.numberOfLeadingZeros(first) - 23

        if (first == 0 || length > 8 || offset + length > bytes.size) {
            throw MalformedFileException("invalid variable-length integer")
        }

        var value = if (keepMarker) {
            first.toLong()
        } else {
            (first and (0xFF shr length)).toLong()
        }

        for (i in 1 until length) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }

        return value to length
    }

    private fun uint(bytes: ByteArray): Long = bytes.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
}
