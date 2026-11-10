import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MkvmergeArgumentsTest {

    private fun copied(
        streamIndex: Int
    ) = Mapping(
        index = streamIndex - 1,
        streamIndex = streamIndex,
        codec = "dts",
        action = COPY,
        language = null,
        title = null
    )

    @Test
    fun `takes video and ac3 from intermediate and copied tracks from source`() {
        val audio = listOf(copied(1), copied(1).copy(action = AC3), copied(3))
        val subtitles = listOf(copied(7), copied(5))

        assertEquals(
            "-o out.mkv intermediate.mkv -D -a 1,3 -s 7,5 movie.mkv --track-order 0:0,1:1,0:1,1:3,1:7,1:5",
            MkvmergeArguments().render(audio, subtitles, "intermediate.mkv", "movie.mkv", "out.mkv")
        )
    }

    @Test
    fun `numbers several ac3 tracks in intermediate in order`() {
        val audio = listOf(copied(2), copied(2).copy(action = AC3), copied(1), copied(1).copy(action = AC3))

        assertEquals(
            "-o out.mkv intermediate.mkv -D -a 2,1 -S movie.mkv --track-order 0:0,1:2,0:1,1:1,0:2",
            MkvmergeArguments().render(audio, emptyList(), "intermediate.mkv", "movie.mkv", "out.mkv")
        )
    }

    @Test
    fun `sets language only on copied tracks`() {
        val audio = listOf(copied(1).copy(language = "deu"), copied(1).copy(action = AC3, language = "deu"))

        assertEquals(
            "-o out.mkv intermediate.mkv -D -a 1 --language 1:deu -S movie.mkv --track-order 0:0,1:1,0:1",
            MkvmergeArguments().render(audio, emptyList(), "intermediate.mkv", "movie.mkv", "out.mkv")
        )
    }

    @Test
    fun `names copied tracks and leaves ac3 names to the first pass`() {
        val audio = listOf(
            copied(1).copy(language = "deu", title = "Surround 5.1 (DTS-HD MA)"),
            copied(1).copy(action = AC3, title = "Surround 5.1 (AC3)")
        )

        assertEquals(
            "-o out.mkv intermediate.mkv -D -a 1 --language 1:deu --track-name 1:\"Surround 5.1 (DTS-HD MA)\" " +
                "-S movie.mkv --track-order 0:0,1:1,0:1",
            MkvmergeArguments().render(audio, emptyList(), "intermediate.mkv", "movie.mkv", "out.mkv")
        )
    }

    @Test
    fun `takes no audio from source without copied audio`() {
        assertEquals(
            "-o out.mkv intermediate.mkv -D -A -S movie.mkv --track-order 0:0",
            MkvmergeArguments().render(emptyList(), emptyList(), "intermediate.mkv", "movie.mkv", "out.mkv")
        )
    }
}
