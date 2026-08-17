package tech.kzen.auto.plugin.model.data;


import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Characterization suite for the per-record staging buffer that frames are copied into.
 */
class DataRecordBufferTest {
    private static final int blockSize = 32;


    private static DataBlockBuffer textBlockOf(String content, int... frameLengths) {
        DataBlockBuffer block = DataBlockBuffer.ofText(blockSize);
        content.getChars(0, content.length(), block.chars, 0);
        block.charsLength = content.length();
        addFrames(block, frameLengths);
        return block;
    }


    private static DataBlockBuffer binaryBlockOf(String content, int... frameLengths) {
        DataBlockBuffer block = DataBlockBuffer.ofBinary(blockSize);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, block.bytes, 0, bytes.length);
        block.bytesLength = bytes.length;
        addFrames(block, frameLengths);
        return block;
    }


    private static void addFrames(DataBlockBuffer block, int... frameLengths) {
        int offset = 0;
        for (int frameLength : frameLengths) {
            block.frames.add(offset, frameLength);
            offset += frameLength;
        }
    }


    private static String charContent(DataRecordBuffer buffer) {
        return new String(buffer.chars, 0, buffer.charsLength);
    }


    private static String byteContent(DataRecordBuffer buffer) {
        return new String(buffer.bytes, 0, buffer.bytesLength, StandardCharsets.UTF_8);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void freshBufferOwnsNoCapacityAndReportsZeroLength() {
        DataRecordBuffer buffer = new DataRecordBuffer();

        assertEquals(0, buffer.bytes.length);
        assertEquals(0, buffer.chars.length);
        assertEquals(0, buffer.length());
    }


    @Test
    void ensureCapacityGrowsButNeverShrinks() {
        DataRecordBuffer buffer = new DataRecordBuffer();

        buffer.ensureByteCapacity(6);
        buffer.ensureCharCapacity(4);
        assertEquals(6, buffer.bytes.length);
        assertEquals(4, buffer.chars.length);

        buffer.ensureByteCapacity(2);
        buffer.ensureCharCapacity(2);
        assertEquals(6, buffer.bytes.length);
        assertEquals(4, buffer.chars.length);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void setFrameReplacesTheContentFromATextBlock() {
        DataBlockBuffer block = textBlockOf("abcdefgh", 3, 5);
        DataRecordBuffer buffer = new DataRecordBuffer();

        buffer.setFrame(block, 0);
        assertEquals("abc", charContent(buffer));
        assertEquals(3, buffer.length());

        buffer.setFrame(block, 1);
        assertEquals("defgh", charContent(buffer));
        assertEquals(5, buffer.length());
    }


    @Test
    void addFrameAppendsToTheContentAlreadyStaged() {
        DataBlockBuffer block = textBlockOf("abcdefgh", 3, 5);
        DataRecordBuffer buffer = new DataRecordBuffer();

        buffer.setFrame(block, 0);
        buffer.addFrame(block, 1);

        assertEquals("abcdefgh", charContent(buffer));
        assertEquals(8, buffer.length());
    }


    @Test
    void binaryBlockFramesStageOnTheByteSide() {
        DataBlockBuffer block = binaryBlockOf("01234567", 4, 4);
        DataRecordBuffer buffer = new DataRecordBuffer();

        buffer.setFrame(block, 0);
        assertEquals("0123", byteContent(buffer));
        assertEquals(0, buffer.charsLength);

        buffer.addFrame(block, 1);
        assertEquals("01234567", byteContent(buffer));
        assertEquals(8, buffer.length());
    }


    @Test
    void stagingABinaryFrameClearsAPriorTextLength() {
        // The text frame is deliberately LONGER than the binary one that replaces it: length() takes the max of
        // the two sides, so a stale char count would outlive the content it described and win.
        DataRecordBuffer buffer = new DataRecordBuffer();
        buffer.setFrame(textBlockOf("abcdefgh", 5, 3), 0);

        buffer.setFrame(binaryBlockOf("01234567", 4, 4), 0);

        assertEquals(0, buffer.charsLength);
        assertEquals(4, buffer.bytesLength);
        assertEquals(4, buffer.length());
        assertEquals("0123", byteContent(buffer));
    }


    @Test
    void stagingATextFrameClearsAPriorBinaryLength() {
        DataRecordBuffer buffer = new DataRecordBuffer();
        buffer.setFrame(binaryBlockOf("01234567", 5, 3), 0);

        buffer.setFrame(textBlockOf("abcdefgh", 4, 4), 0);

        assertEquals(0, buffer.bytesLength);
        assertEquals(4, buffer.charsLength);
        assertEquals(4, buffer.length());
        assertEquals("abcd", charContent(buffer));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void clearResetsLengthsWithoutWipingTheBackingArrays() {
        DataBlockBuffer block = textBlockOf("abcdefgh", 3, 5);
        DataRecordBuffer buffer = new DataRecordBuffer();
        buffer.setFrame(block, 0);
        buffer.addFrame(block, 1);

        buffer.clear();

        assertEquals(0, buffer.length());
        assertEquals(0, buffer.charsLength);
        assertEquals("abcdefgh", new String(buffer.chars, 0, 8));

        buffer.setFrame(block, 1);
        assertEquals("defgh", charContent(buffer));
    }


    @Test
    void copyReplicatesContentAndLength() {
        DataBlockBuffer block = textBlockOf("abcdefgh", 3, 5);
        DataRecordBuffer source = new DataRecordBuffer();
        source.setFrame(block, 1);

        DataRecordBuffer target = new DataRecordBuffer();
        target.copy(source);

        assertEquals("defgh", charContent(target));
        assertEquals(5, target.length());
        assertNotSame(source.chars, target.chars);
    }


    @Test
    void copyOfAnEmptySourceResetsLengthAndKeepsCapacity() {
        DataBlockBuffer block = textBlockOf("abcdefgh", 3, 5);
        DataRecordBuffer target = new DataRecordBuffer();
        target.setFrame(block, 1);

        target.copy(new DataRecordBuffer());

        assertEquals(0, target.length());
        assertEquals(5, target.chars.length);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void nioViewsAreCachedAndRewoundOnEachRequest() {
        DataRecordBuffer buffer = new DataRecordBuffer();
        buffer.ensureByteCapacity(8);
        buffer.ensureCharCapacity(8);

        ByteBuffer firstBytes = buffer.initializedByteBuffer(4);
        ByteBuffer secondBytes = buffer.initializedByteBuffer(6);
        assertSame(firstBytes, secondBytes);
        assertEquals(6, secondBytes.limit());
        assertEquals(0, secondBytes.position());

        CharBuffer firstChars = buffer.initializedCharBuffer(4);
        CharBuffer secondChars = buffer.initializedCharBuffer(6);
        assertSame(firstChars, secondChars);
        assertEquals(6, secondChars.limit());
        assertEquals(0, secondChars.position());
    }


    @Test
    void growingCapacityDiscardsTheCachedNioView() {
        DataRecordBuffer buffer = new DataRecordBuffer();
        buffer.ensureByteCapacity(8);
        ByteBuffer before = buffer.initializedByteBuffer(4);

        buffer.ensureByteCapacity(64);
        ByteBuffer after = buffer.initializedByteBuffer(4);

        assertNotSame(before, after);
        assertEquals(64, after.capacity());
    }
}
