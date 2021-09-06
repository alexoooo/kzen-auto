package tech.kzen.auto.server.util

import org.junit.Test
import tech.kzen.auto.server.objects.pipeline.exec.input.model.ReadResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ReadResultTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun endOfDataTest() {
        assertTrue(ReadResult.endOfData.isEndOfData())
//        assertEquals(Path.of("C:\\"), parsed)
    }


    @Test
    fun readZero() {
        val result = ReadResult.of(0, 0)
        assertEquals(0, result.byteCount())
        assertEquals(0, result.rawByteCount())
    }


    @Test
    fun readOne() {
        val result = ReadResult.of(1, 1)
        assertEquals(1, result.byteCount())
        assertEquals(1, result.rawByteCount())
    }


    @Test
    fun readCompressed() {
        val result = ReadResult.of(100, 10)
        assertEquals(100, result.byteCount())
        assertEquals(10, result.rawByteCount())
    }
}