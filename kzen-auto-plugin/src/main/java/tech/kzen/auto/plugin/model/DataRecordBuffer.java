package tech.kzen.auto.plugin.model;


import java.util.Arrays;


public class DataRecordBuffer {
    //-----------------------------------------------------------------------------------------------------------------
    private static final byte[] emptyBytes = new byte[0];
    private static final char[] emptyChars = new char[0];


    //-----------------------------------------------------------------------------------------------------------------
    public byte[] bytes = emptyBytes;
    public int bytesLength;

    public char[] chars = emptyChars;
    public int charsLength;


    //-----------------------------------------------------------------------------------------------------------------
    public int length() {
        return Math.max(bytesLength, charsLength);
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void clear() {
        bytesLength = 0;
        charsLength = 0;
    }


    public void setFrame(DataBlockBuffer dataBlockBuffer, int frameIndex) {
        DataFrameBuffer frames = dataBlockBuffer.frames;
        int offset = frames.offsets[frameIndex];
        int length = frames.lengths[frameIndex];

        char[] eventChars = dataBlockBuffer.chars;
        if (eventChars == null) {
            if (bytes.length < length) {
                bytes = new byte[length];
            }
            System.arraycopy(dataBlockBuffer.bytes, offset, bytes, 0, length);
            bytesLength = length;
        }
        else {
            if (chars.length < length) {
                chars = new char[length];
            }
            System.arraycopy(eventChars, offset, chars, 0, length);
            charsLength = length;
        }
    }


    public void addFrame(DataBlockBuffer dataBlockBuffer, int frameIndex) {
        DataFrameBuffer frames = dataBlockBuffer.frames;
        int offset = frames.offsets[frameIndex];
        int length = frames.lengths[frameIndex];

        char[] eventChars = dataBlockBuffer.chars;
        if (eventChars == null) {
            if (bytes.length < bytesLength + length) {
                bytes = Arrays.copyOf(bytes, bytesLength + length);
            }
            System.arraycopy(dataBlockBuffer.bytes, offset, bytes, bytesLength, length);
            bytesLength += length;
        }
        else {
            if (chars.length < charsLength + length) {
                chars = Arrays.copyOf(chars, charsLength + length);
            }
            System.arraycopy(eventChars, offset, chars, charsLength, length);
            charsLength += length;
        }
    }


    public void copy(DataRecordBuffer source) {
        if (source.bytesLength != 0) {
            if (bytes.length < source.bytesLength) {
                bytes = Arrays.copyOf(source.bytes, source.bytesLength);
            }
            else {
                System.arraycopy(source.bytes, 0, bytes, 0, source.bytesLength);
            }
        }
        bytesLength = source.bytesLength;

        if (source.charsLength != 0) {
            if (chars.length < source.charsLength) {
                chars = Arrays.copyOf(source.chars, source.charsLength);
            }
            else {
                System.arraycopy(source.chars, 0, chars, 0, source.charsLength);
            }
        }
        charsLength = source.charsLength;
    }
}
