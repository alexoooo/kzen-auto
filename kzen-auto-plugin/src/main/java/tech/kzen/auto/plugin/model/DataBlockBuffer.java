package tech.kzen.auto.plugin.model;


import java.nio.ByteBuffer;
import java.nio.CharBuffer;


public class DataBlockBuffer {
    //-----------------------------------------------------------------------------------------------------------------
    public static final int defaultBytesSize = 64 * 1024;

    // https://stijndewitt.com/2014/08/09/max-bytes-in-a-utf-8-char/
    private static final int maxUnicodeSize = 3;


    //-----------------------------------------------------------------------------------------------------------------
    public static DataBlockBuffer ofTextOrBinary(boolean isText) {
        return isText
                ? ofText()
                : ofBinary();
    }


    public static DataBlockBuffer ofTextOrBinary(boolean isText, int bytesSize) {
        return isText
                ? ofText(bytesSize)
                : ofBinary(bytesSize);
    }


    public static DataBlockBuffer ofBinary() {
        return ofBinary(defaultBytesSize);
    }


    public static DataBlockBuffer ofText() {
        return ofText(defaultBytesSize);
    }


    public static DataBlockBuffer ofBinary(int bytesSize) {
        return new DataBlockBuffer(bytesSize, false);
    }


    public static DataBlockBuffer ofText(int bytesSize) {
        return new DataBlockBuffer(bytesSize, true);
    }


    //-----------------------------------------------------------------------------------------------------------------
    public boolean endOfData;

//    public URI inputKey;
//    public String innerExtension;

    public final byte[] bytes;
    public int bytesLength;

    public final char[] chars;
    public int charsLength;

    public boolean endOfStream;

    public final DataFrameBuffer frames;

    public final ByteBuffer byteBuffer;
    public final CharBuffer charBuffer;


    //-----------------------------------------------------------------------------------------------------------------
    private DataBlockBuffer(int bytesSize, boolean text) {
//        inputKey = URI.create("");
//        innerExtension = "";

        bytes = new byte[bytesSize];

        frames = new DataFrameBuffer();

        byteBuffer = ByteBuffer.wrap(bytes);

        if (text) {
            chars = new char[bytesSize + maxUnicodeSize];
            charBuffer = CharBuffer.wrap(chars);
        }
        else {
            chars = null;
            charBuffer = null;
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    public void setInput(URI inputKey, String innerExtension) {
//        this.inputKey = inputKey;
//        this.innerExtension = innerExtension;
//    }


    public void endStream() {
        bytesLength = 0;
        endOfStream = true;
    }


    public void readNext(int bytesLength) {
        this.bytesLength = bytesLength;
        endOfStream = false;
    }
}
