import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FfmpegWrapperImplTest {

    @TempDir
    lateinit var tempDir: File

    private fun fakeExecutable(script: String) = File(tempDir, "fake-ffmpeg")
        .apply {
            writeText("#!/bin/sh\n$script\n")
            setExecutable(true)
        }.absolutePath

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `reads output larger than the pipe buffer without hanging`() {
        val executable =
            fakeExecutable("i=0; while [ \$i -lt 5000 ]; do echo \"line \$i xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\" >&2; i=\$((i+1)); done; exit 1")

        val lines = FfmpegWrapperImpl(executable).read("somefile.mkv")

        assertEquals(5000, lines.size)
        assertEquals("line 4999 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", lines.last())
    }

    @Test
    fun `passes the file name to ffmpeg and merges stderr into the result`() {
        val executable = fakeExecutable("echo \"args: \$@\" >&2")

        val lines = FfmpegWrapperImpl(executable).read("some file.mkv")

        assertEquals(listOf("args: -i some file.mkv"), lines)
    }
}
