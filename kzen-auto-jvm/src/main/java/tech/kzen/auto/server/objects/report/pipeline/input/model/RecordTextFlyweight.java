package tech.kzen.auto.server.objects.report.pipeline.input.model;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class RecordTextFlyweight
        implements CharSequence
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final RecordTextFlyweight empty = standalone("");


    public static final long[] decimalLongPowers = {
            1,
            10,
            100,
            1_000,
            10_000,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000,
            1_000_000_000,
            10_000_000_000L,
            100_000_000_000L,
            1_000_000_000_000L,
            10_000_000_000_000L,
            100_000_000_000_000L,
            1_000_000_000_000_000L,
            10_000_000_000_000_000L,
            100_000_000_000_000_000L
    };


    public static final int maxLongDecimalLength = Long.valueOf(999999999999999999L).toString().length();


    public static RecordTextFlyweight standalone(String value) {
        char[] chars = value.toCharArray();
        RecordItemBuffer buffer = RecordItemBuffer.ofSingle(chars, 0, chars.length);
        RecordTextFlyweight flyweight = new RecordTextFlyweight();
        flyweight.selectHostValue(buffer,0, chars.length);
        return flyweight;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private RecordItemBuffer host;
//    private int fieldIndex = -1;

    private int valueOffset = -1;
    private int valueLength = -1;

    private int hashCodeCache = -1;


    //-----------------------------------------------------------------------------------------------------------------
    public void selectHost(RecordItemBuffer host) {
        this.host = host;
        valueOffset = -1;
        valueLength = -1;
    }


    public void selectHostValue(RecordItemBuffer host, int valueOffset, int valueLength) {
        this.host = host;
        selectValue(valueOffset, valueLength);
    }


    public void selectHostField(RecordItemBuffer host, int fieldIndex) {
        this.host = host;
        selectField(fieldIndex);
    }


    public void selectField(int fieldIndex) {
        int startIndex;
        int endIndex;
        if (fieldIndex == 0) {
            startIndex = 0;
            endIndex = host.fieldEnds[0];
        }
        else {
            startIndex = host.fieldEnds[fieldIndex - 1];
            endIndex = host.fieldEnds[fieldIndex];
        }

        int length = endIndex - startIndex;

        selectValue(startIndex, length);
    }


    public void selectValue(int valueOffset, int valueLength) {
        this.valueOffset = valueOffset;
        this.valueLength = valueLength;
        hashCodeCache = -1;
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
    public RecordTextFlyweight detach() {
        RecordItemBuffer buffer = RecordItemBuffer.ofSingle(host.fieldContents, valueOffset, valueLength);
        RecordTextFlyweight detached = new RecordTextFlyweight();
        detached.host = buffer;
//        detached.fieldIndex = 0;
        detached.valueOffset = 0;
        detached.valueLength = valueLength;
        detached.hashCodeCache = hashCodeCache;
        return detached;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void trim() {
        char[] contents = host.fieldContents;

        int initialLength = valueLength;
        for (int i = 0; i < initialLength; i++) {
            if (! Character.isWhitespace(contents[valueOffset])) {
                break;
            }

            valueOffset++;
            valueLength--;
        }

        int afterLeftTrimLength = valueLength;
        for (int i = (afterLeftTrimLength - 1); i >= 0; i--) {
            if (! Character.isWhitespace(contents[valueOffset + i])) {
                break;
            }

            valueLength--;
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    public boolean isDecimal() {
        char[] contents = host.fieldContents;
        int len = valueLength;
        int offset = valueOffset;

        var dotCount = 0;
        var digitCount = 0;

        var i = 0;
        while (i < len) {
            char nextChar = contents[offset + i++];

            if (nextChar == '.') {
                dotCount++;
            }
            else if ('0' <= nextChar && nextChar <= '9') {
                digitCount++;
            }
            else {
                return false;
            }
        }

        return digitCount > 0 && dotCount <= 1;
    }


    public long toLong() {
        return toLong(0, valueLength);
    }

    // Shadowed from Java Util Lang, but specialized
    public long toLong(int offset, int length) {
        if (offset + length > valueLength) {
            throw new NumberFormatException(
                    offset + " - " + length + " - " + valueOffset + " - " + valueLength + " - " + toString());
        }
        if (length <= 0) {
            throw new NumberFormatException("offset = " + offset+ ", length = " + length + ": " + toString());
        }

        char[] contents = host.fieldContents;
        int relativeOffset = valueOffset + offset;

        boolean negative = false;
        int i = 0;
        long limit = -Long.MAX_VALUE;
        int digit;

        char firstChar = contents[relativeOffset + i];
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = Long.MIN_VALUE;
            }
            else if (firstChar != '+') {
                throw new NumberFormatException(toString());
            }
            i++;
            if (length == 1) { // Cannot have lone "+" or "-"
                throw new NumberFormatException(toString());
            }
        }

        long multmin = limit / 10;
        var result = 0L;
        while (i < length) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = contents[relativeOffset + i++] - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException(toString());
            }
            if (result < multmin) {
                throw new NumberFormatException(toString());
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(toString());
            }
            result -= digit;
        }

        return negative ? result : -result;
    }


    public double toDouble() {
        char[] contents = host.fieldContents;
        int len = valueLength;
        int offset = valueOffset;

        int pointIndex = -1;
        for (int i = 0; i < len; i++) {
            if (contents[offset + i] == '.') {
                pointIndex = i;
                break;
            }
        }

        if (pointIndex == -1) {
            return (double) toLong();
        }
        else if (pointIndex == len - 1) {
            return (double) toLong(0, len - 1);
        }

        long wholePart =
                pointIndex == 0  || pointIndex == 1 && (contents[offset] == '+' || contents[offset] == '-')
                ? 0
                : Math.abs(toLong(0, pointIndex));

        // https://math.stackexchange.com/questions/64042/what-are-the-numbers-before-and-after-the-decimal-point-referred-to-in-mathemati/438718#438718
        int fractionDigits = len - pointIndex - 1;
        int fractionLeadingZeroes = 0;
        for (int i = 1; i <= fractionDigits; i++) {
            if (contents[offset + pointIndex + i] != '0') {
                break;
            }
            fractionLeadingZeroes++;
        }

        int fractionDigitsWithoutLeadingZeroes = fractionDigits - fractionLeadingZeroes;
        long fractionAsLongWithoutLeadingZeroes =
                fractionDigitsWithoutLeadingZeroes == 0
                ? 0
                : toLong(pointIndex + fractionLeadingZeroes + 1, fractionDigitsWithoutLeadingZeroes);

        double fractionalPartWithoutLeadingZeroes =
                (double) fractionAsLongWithoutLeadingZeroes / decimalLongPowers[fractionDigitsWithoutLeadingZeroes];

        double fractionalPart =
                fractionLeadingZeroes == 0
                ? fractionalPartWithoutLeadingZeroes
                : fractionalPartWithoutLeadingZeroes / decimalLongPowers[fractionLeadingZeroes];

        double value = wholePart + fractionalPart;
        return contents[offset] == '-' ? -value : value;
    }


    public double toDoubleOrNan() {
        int len = valueLength;
        if (len == 0) {
            return Double.NaN;
        }

        char[] contents = host.fieldContents;
        int offset = valueOffset;

        int leadingZeroes = 0;
        int pointIndex = -1;
        for (int i = 0; i < len; i++) {
            char nextChar = contents[offset + i];
            if (nextChar == '.') {
                if (pointIndex != -1 ||
                        len == 1 ||
                        len == 2 && (contents[offset] == '+' || contents[offset] == '-')
                ) {
                    return Double.NaN;
                }
                else {
                    pointIndex = i;
                }
            }
            else if (nextChar == '+' || nextChar == '-') {
                if (i != 0 || len == 1) {
                    return Double.NaN;
                }
            }
            else if (! ('0' <= nextChar && nextChar <= '9')) {
                return Double.NaN;
            }
            else if (nextChar == '0' && leadingZeroes == i) {
                leadingZeroes++;
            }
        }

        if (pointIndex == -1) {
            if (valueLength - leadingZeroes > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong();
        }
        else if (pointIndex == len - 1) {
            if (valueLength - leadingZeroes - 1 > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong(0, len - 1);
        }

        long wholePart;
        if (pointIndex == 0 || pointIndex == 1 && (contents[offset] == '+' || contents[offset] == '-')) {
            wholePart = 0;
        }
        else if (pointIndex - leadingZeroes > maxLongDecimalLength) {
            return Double.NaN;
        }
        else {
            wholePart = Math.abs(toLong(0, pointIndex));
        }

        int fractionDigits = len - pointIndex - 1;
        int fractionLeadingZeroes = 0;
        for (int i = 1; i <= fractionDigits; i++) {
            if (contents[offset + pointIndex + i] != '0') {
                break;
            }
            fractionLeadingZeroes++;
        }

        int fractionDigitsWithoutLeadingZeroes = fractionDigits - fractionLeadingZeroes;
        long fractionAsLongWithoutLeadingZeroes;
        if (fractionDigitsWithoutLeadingZeroes == 0) {
            fractionAsLongWithoutLeadingZeroes = 0;
        }
        else if (fractionDigitsWithoutLeadingZeroes > maxLongDecimalLength) {
            return Double.NaN;
        }
        else {
            fractionAsLongWithoutLeadingZeroes = toLong(
                    pointIndex + fractionLeadingZeroes + 1, fractionDigitsWithoutLeadingZeroes);
        }

        double fractionalPartWithoutLeadingZeroes =
                (double) fractionAsLongWithoutLeadingZeroes / decimalLongPowers[fractionDigitsWithoutLeadingZeroes];

        double fractionalPart =
                fractionLeadingZeroes == 0
                ? fractionalPartWithoutLeadingZeroes
                : fractionalPartWithoutLeadingZeroes / decimalLongPowers[fractionLeadingZeroes];

        double value = wholePart + fractionalPart;
        return contents[offset] == '-' ? -value : value;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public String toString() {
        return new String(host.fieldContents, valueOffset, valueLength);
    }


    @Override
    public int hashCode() {
        if (hashCodeCache != -1) {
            return hashCodeCache;
        }

        char[] contents = host.fieldContents;
        int offset = valueOffset;
        int end = offset + valueLength;

        var result = 1;
        for (int i = offset; i < end; i++) {
            result = 31 * result + contents[i];
        }

        hashCodeCache = result;
        return result;
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other.getClass() != RecordTextFlyweight.class) {
            return false;
        }

        RecordTextFlyweight that = (RecordTextFlyweight) other;

        int len = valueLength;
        if (len != that.valueLength) {
            return false;
        }

        if (hashCode() != that.hashCode()) {
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
