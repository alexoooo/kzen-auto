package tech.kzen.auto.plugin.model.data;


import org.junit.jupiter.api.Test;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterization suite for the fixed-capacity read block handed to the parse pipeline.
 */
class DataBlockBufferTest {
    private static final int smallBytesSize = 16;


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void textBlockAllocatesCharsWithMultiByteHeadroom() {
        DataBlockBuffer buffer = DataBlockBuffer.ofText(smallBytesSize);

        assertEquals(smallBytesSize, buffer.bytes.length);
        assertEquals(smallBytesSize + DataBlockBuffer.maxUnicodeSize, buffer.chars.length);
        assertNotNull(buffer.byteBuffer);
        assertNotNull(buffer.charBuffer);
        assertNotNull(buffer.frames);
    }


    @Test
    void binaryBlockHasNoCharacterSide() {
        DataBlockBuffer buffer = DataBlockBuffer.ofBinary(smallBytesSize);

        assertEquals(smallBytesSize, buffer.bytes.length);
        assertNull(buffer.chars);
        assertNull(buffer.charBuffer);
        assertNotNull(buffer.byteBuffer);
    }


    @Test
    void defaultFactoriesUseTheDefaultBlockSize() {
        assertEquals(DataBlockBuffer.defaultBytesSize, DataBlockBuffer.ofText().bytes.length);
        assertEquals(DataBlockBuffer.defaultBytesSize, DataBlockBuffer.ofBinary().bytes.length);
    }


    @Test
    void encodingSpecSelectsTextOrBinaryLayout() {
        assertNotNull(DataBlockBuffer.ofTextOrBinary(DataEncodingSpec.Companion.getUtf8()).chars);
        assertNull(DataBlockBuffer.ofTextOrBinary(DataEncodingSpec.Companion.getBinary()).chars);
        assertNotNull(DataBlockBuffer.ofTextOrBinary(true, smallBytesSize).chars);
        assertNull(DataBlockBuffer.ofTextOrBinary(false, smallBytesSize).chars);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void readNextRecordsTheByteCountAndClearsEndOfData() {
        DataBlockBuffer buffer = DataBlockBuffer.ofText(smallBytesSize);
        buffer.setEndOfData();

        buffer.readNext(5);

        assertEquals(5, buffer.bytesLength);
        assertFalse(buffer.endOfData);
    }


    @Test
    void setEndOfDataZeroesTheByteCount() {
        DataBlockBuffer buffer = DataBlockBuffer.ofText(smallBytesSize);
        buffer.readNext(5);

        buffer.setEndOfData();

        assertEquals(0, buffer.bytesLength);
        assertTrue(buffer.endOfData);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void textBlockRendersItsDecodedCharacters() {
        DataBlockBuffer buffer = DataBlockBuffer.ofText(smallBytesSize);
        "hi".getChars(0, 2, buffer.chars, 0);
        buffer.charsLength = 2;

        assertEquals("hi", buffer.toString());
    }


    @Test
    void binaryBlockRendersItsRawByteValues() {
        DataBlockBuffer buffer = DataBlockBuffer.ofBinary(smallBytesSize);
        byte[] source = "hi".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(source, 0, buffer.bytes, 0, source.length);
        buffer.bytesLength = source.length;

        assertEquals("[104, 105]", buffer.toString());
    }
}
