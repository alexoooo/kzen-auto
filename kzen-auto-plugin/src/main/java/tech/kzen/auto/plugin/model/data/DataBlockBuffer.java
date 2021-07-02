package tech.kzen.auto.plugin.model.data;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;


public class DataBlockBuffer {
    //-----------------------------------------------------------------------------------------------------------------
    public static final int defaultBytesSize = 64 * 1024;

    // https://stijndewitt.com/2014/08/09/max-bytes-in-a-utf-8-char/
    public static final int maxUnicodeSize = 3;


    //-----------------------------------------------------------------------------------------------------------------
    public static DataBlockBuffer ofTextOrBinary(DataEncodingSpec dataEncodingSpec) {
        return ofTextOrBinary(dataEncodingSpec.getTextEncoding() != null);
    }


    public static DataBlockBuffer ofTextOrBinary(boolean isText) {
        return isText
                ? ofText()
                : ofBinary();
    }


    @NotNull
    public static DataBlockBuffer ofTextOrBinary(boolean isText, int bytesSize) {
        return isText
                ? ofText(bytesSize)
                : ofBinary(bytesSize);
    }


    @NotNull
    public static DataBlockBuffer ofText() {
        return ofText(defaultBytesSize);
    }


    @NotNull
    public static DataBlockBuffer ofBinary() {
        return ofBinary(defaultBytesSize);
    }


    @NotNull
    public static DataBlockBuffer ofText(int bytesSize) {
        return new DataBlockBuffer(bytesSize, true);
    }


    @NotNull
    public static DataBlockBuffer ofBinary(int bytesSize) {
        return new DataBlockBuffer(bytesSize, false);
    }


    //-----------------------------------------------------------------------------------------------------------------
    public boolean endOfData;

    public final byte[] bytes;
    public int bytesLength;

    public final char[] chars;
    public int charsLength;

    public final DataFrameBuffer frames;

    public final ByteBuffer byteBuffer;
    public final CharBuffer charBuffer;


    //-----------------------------------------------------------------------------------------------------------------
    private DataBlockBuffer(int bytesSize, boolean text) {
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
    public void setEndOfData() {
        bytesLength = 0;
        endOfData = true;
    }


    public void readNext(int bytesLength) {
        this.bytesLength = bytesLength;
        endOfData = false;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        if (chars == null) {
            return Arrays.toString(Arrays.copyOf(bytes, bytesLength));
        }
        else {
            return new String(chars, 0, charsLength);
        }
    }
}
