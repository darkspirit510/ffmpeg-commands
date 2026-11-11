// Reads the index (Cues) of a Matroska file. mkvmerge writes a cue for every subtitle line, so the cue times show how
// long a subtitle track goes without a packet.
interface CueReader {
    // Cue times in seconds for every subtitle track, keyed by ffmpeg's stream index; null when the file has no readable
    // Cues element (not Matroska, not readable, or not indexed).
    fun subtitleCueTimes(file: String): Map<Int, List<Double>>?
}
