package tech.kzen.auto.plugin.model.record;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class FlatFileRecordField
        implements CharSequence
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final FlatFileRecordField empty = standalone("");


    public static FlatFileRecordField standalone(String value) {
        char[] chars = value.toCharArray();
        FlatFileRecord buffer = FlatFileRecord.ofSingle(chars, 0, chars.length);
        FlatFileRecordField flyweight = new FlatFileRecordField();
        flyweight.selectHostValue(buffer,0, 0, chars.length);
        return flyweight;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private FlatFileRecord host;
    private int[] fieldEnds;

    // NB: not part of equality or hash code, used for accessing doubleOrNan cache in host
    private int fieldIndex = -1;

    private int valueOffset = -1;
    private int valueLength = -1;


    //-----------------------------------------------------------------------------------------------------------------
    public void selectHost(FlatFileRecord host) {
        this.host = host;
        fieldEnds = host.fieldEnds;

        fieldIndex = -1;
        valueOffset = -1;
        valueLength = -1;
    }


    public void selectHostValue(FlatFileRecord host, int fieldIndex, int valueOffset, int valueLength) {
        this.host = host;
        fieldEnds = host.fieldEnds;

        selectField(fieldIndex, valueOffset, valueLength);
    }


    public void selectHostField(FlatFileRecord host, int fieldIndex) {
        this.host = host;
        fieldEnds = host.fieldEnds;

        selectField(fieldIndex);
    }


    public void selectField(int fieldIndex) {
        this.fieldIndex = fieldIndex;

//        int startIndex = host.contentStart(fieldIndex);
        int startIndex = fieldIndex == 0 ? 0 : fieldEnds[fieldIndex - 1];
        valueOffset = startIndex;
        valueLength = fieldEnds[fieldIndex] - startIndex;
    }


    public void selectField(int fieldIndex, int valueOffset, int valueLength) {
        this.fieldIndex = fieldIndex;
        this.valueOffset = valueOffset;
        this.valueLength = valueLength;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int length() {
        return valueLength;
    }


    @Override
    public char charAt(int index) {
        return host.fieldContents[valueOffset + index];
    }


    @Override
    public CharSequence subSequence(int start, int end) {
        throw new UnsupportedOperationException();
    }


    //-----------------------------------------------------------------------------------------------------------------
    public FlatFileRecordField detach() {
        FlatFileRecord buffer = FlatFileRecord.ofSingle(host.fieldContents, valueOffset, valueLength);
        FlatFileRecordField detached = new FlatFileRecordField();
        detached.host = buffer;
        detached.valueOffset = 0;
        detached.valueLength = valueLength;
        return detached;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean isEmpty() {
        return valueLength == 0;
    }


    public boolean isBlankOrEmpty() {
        if (valueLength == 0) {
            return true;
        }

        char[] contents = host.fieldContents;
        for (int i = 0; i < valueLength; i++) {
            if (contents[valueOffset + i] != ' ') {
                return false;
            }
        }

        return true;
    }


    private boolean isCached() {
        return host.isCached(fieldIndex);
    }


    public double toDoubleOrNan() {
        return host.cachedDoubleOrNan(fieldIndex);
    }


    public long goodHash() {
        return host.cachedHash(fieldIndex);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public String toString() {
        return new String(host.fieldContents, valueOffset, valueLength);
    }


    @Override
    public int hashCode() {
        return (int) goodHash();
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other.getClass() != FlatFileRecordField.class) {
            return false;
        }

        FlatFileRecordField that = (FlatFileRecordField) other;

        int len = valueLength;
        if (len != that.valueLength) {
            return false;
        }

        if (isCached() && that.isCached() && goodHash() != that.goodHash()) {
            return false;
        }

        char[] contents = host.fieldContents;
        int offset = valueOffset;
        char[] thatContents = that.host.fieldContents;
        int thatValueOffset = that.valueOffset;

        return Arrays.equals(
                contents, offset, offset + len,
                thatContents, thatValueOffset, thatValueOffset + len);
    }
}
