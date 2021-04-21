package tech.kzen.auto.server.objects.report.pipeline.input.model;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class RecordFieldFlyweight
        implements CharSequence
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final RecordFieldFlyweight empty = standalone("");


    public static RecordFieldFlyweight standalone(String value) {
        char[] chars = value.toCharArray();
        FlatDataRecord buffer = FlatDataRecord.ofSingle(chars, 0, chars.length);
        RecordFieldFlyweight flyweight = new RecordFieldFlyweight();
        flyweight.selectHostValue(buffer,0, 0, chars.length);
        return flyweight;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private FlatDataRecord host;

    // NB: not part of equality or hash code, used for accessing doubleOrNan cache in host
    private int fieldIndex = -1;

    private int valueOffset = -1;
    private int valueLength = -1;


    //-----------------------------------------------------------------------------------------------------------------
    public void selectHost(FlatDataRecord host) {
        this.host = host;
        fieldIndex = -1;
        valueOffset = -1;
        valueLength = -1;
    }


    public void selectHostValue(FlatDataRecord host, int fieldIndex, int valueOffset, int valueLength) {
        this.host = host;
        selectField(fieldIndex, valueOffset, valueLength);
    }


    public void selectHostField(FlatDataRecord host, int fieldIndex) {
        this.host = host;
        selectField(fieldIndex);
    }


    public void selectField(int fieldIndex) {
        this.fieldIndex = fieldIndex;

        int startIndex = host.contentStart(fieldIndex);
        valueOffset = startIndex;
        valueLength = host.fieldEnds[fieldIndex] - startIndex;
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
    public RecordFieldFlyweight detach() {
        FlatDataRecord buffer = FlatDataRecord.ofSingle(host.fieldContents, valueOffset, valueLength);
        RecordFieldFlyweight detached = new RecordFieldFlyweight();
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
//        return host.doublesCache[fieldIndex];
        return host.cachedDoubleOrNan(fieldIndex);
    }


    public long goodHash() {
//        return host.hashesCache[fieldIndex];
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

        if (other.getClass() != RecordFieldFlyweight.class) {
            return false;
        }

        RecordFieldFlyweight that = (RecordFieldFlyweight) other;

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
