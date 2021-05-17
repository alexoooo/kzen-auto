package tech.kzen.auto.server.objects.report.pipeline.input.parse;


// Similar to Java Util Lang, but more tolerant and specialized, also it normalizes -0 to 0
// TODO: https://github.com/wrandelshofer/FastDoubleParser
public enum NumberParseUtils {;
    //-----------------------------------------------------------------------------------------------------------------
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


    // NB: shadowed from Integer as char
    private static final char[] digitTens = {
            '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
            '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
            '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
            '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
            '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
            '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
            '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
            '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
            '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    };

    // NB: shadowed from Integer where it is not accessible
    private static final char[] digitOnes = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    };


    //-----------------------------------------------------------------------------------------------------------------
    public static double toDoubleOrNan(char[] contents, int offset, int length) {
        if (length == 0) {
            return Double.NaN;
        }
        char firstChar = contents[offset];

        int leadingZeroes = 0;
        int pointIndex = -1;
        for (int i = 0; i < length; i++) {
            char nextChar = contents[offset + i];
            if (nextChar == '.') {
                if (pointIndex != -1 ||
                        length == 1 ||
                        length == 2 && (firstChar == '+' || firstChar == '-')
                ) {
                    return Double.NaN;
                }
                else {
                    pointIndex = i;
                }
            }
            else if (nextChar == '+' || nextChar == '-') {
                if (i != 0 || length == 1) {
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
            if (length - leadingZeroes > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong(contents, offset, length);
        }
        else if (pointIndex == length - 1) {
            if (length - leadingZeroes - 1 > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong(contents, offset, length - 1);
        }

        long wholePart;
        if (pointIndex == 0 ||
                pointIndex == 1 && (firstChar == '+' || firstChar == '-')) {
            wholePart = 0;
        }
        else if (pointIndex - leadingZeroes > maxLongDecimalLength) {
            return Double.NaN;
        }
        else {
            wholePart = Math.abs(toLong(contents, offset, pointIndex));
        }

        int fractionDigits = length - pointIndex - 1;
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
                    contents,
                    offset + pointIndex + fractionLeadingZeroes + 1,
                    fractionDigitsWithoutLeadingZeroes);
        }

        double fractionalPartWithoutLeadingZeroes =
                (double) fractionAsLongWithoutLeadingZeroes / decimalLongPowers[fractionDigitsWithoutLeadingZeroes];

        double fractionalPart =
                fractionLeadingZeroes == 0
                ? fractionalPartWithoutLeadingZeroes
                : fractionalPartWithoutLeadingZeroes / decimalLongPowers[fractionLeadingZeroes];

        double value = wholePart + fractionalPart;
        return firstChar == '-' && value != 0.0 ? -value : value;
    }


    public static double toDoubleOrNan(String contents) {
        return toDoubleOrNan(contents, 0, contents.length());
    }


    public static double toDoubleOrNan(String contents, int offset, int length) {
        if (length == 0) {
            return Double.NaN;
        }

        char firstChar = contents.charAt(offset);

        int leadingZeroes = 0;
        int pointIndex = -1;
        for (int i = 0; i < length; i++) {
            char nextChar = contents.charAt(offset + i);
            if (nextChar == '.') {
                if (pointIndex != -1 ||
                        length == 1 ||
                        length == 2 && (firstChar == '+' || firstChar == '-')
                ) {
                    return Double.NaN;
                }
                else {
                    pointIndex = i;
                }
            }
            else if (nextChar == '+' || nextChar == '-') {
                if (i != 0 || length == 1) {
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
            if (length - leadingZeroes > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong(contents, offset, length);
        }
        else if (pointIndex == length - 1) {
            if (length - leadingZeroes - 1 > maxLongDecimalLength) {
                return Double.NaN;
            }
            return (double) toLong(contents, offset, length - 1);
        }

        long wholePart;
        if (pointIndex == 0 ||
                pointIndex == 1 && (firstChar == '+' || firstChar == '-')) {
            wholePart = 0;
        }
        else if (pointIndex - leadingZeroes > maxLongDecimalLength) {
            return Double.NaN;
        }
        else {
            wholePart = Math.abs(toLong(contents, offset, pointIndex));
        }

        int fractionDigits = length - pointIndex - 1;
        int fractionLeadingZeroes = 0;
        for (int i = 1; i <= fractionDigits; i++) {
            if (contents.charAt(offset + pointIndex + i) != '0') {
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
                    contents,
                    offset + pointIndex + fractionLeadingZeroes + 1,
                    fractionDigitsWithoutLeadingZeroes);
        }

        double fractionalPartWithoutLeadingZeroes =
                (double) fractionAsLongWithoutLeadingZeroes / decimalLongPowers[fractionDigitsWithoutLeadingZeroes];

        double fractionalPart =
                fractionLeadingZeroes == 0
                ? fractionalPartWithoutLeadingZeroes
                : fractionalPartWithoutLeadingZeroes / decimalLongPowers[fractionLeadingZeroes];

        double value = wholePart + fractionalPart;
        return firstChar == '-' && value != 0.0 ? -value : value;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private static long toLong(char[] contents, int offset, int length) {
        if (length <= 0 || contents.length < offset + length) {
            throw new NumberFormatException(
                    "offset = " + offset+ ", length = " + length + ": " + new String(contents));
        }

        boolean negative = false;
        int i = 0;
        long limit = -Long.MAX_VALUE;
        int digit;

        char firstChar = contents[offset];
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = Long.MIN_VALUE;
            }
            else if (firstChar != '+') {
                throw new NumberFormatException(new String(contents));
            }
            i++;
            if (length == 1) { // Cannot have lone "+" or "-"
                throw new NumberFormatException(new String(contents));
            }
        }

        long multmin = limit / 10;
        var result = 0L;
        while (i < length) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = contents[offset + i++] - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException(new String(contents));
            }
            if (result < multmin) {
                throw new NumberFormatException(new String(contents));
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(new String(contents));
            }
            result -= digit;
        }

        return negative ? result : -result;
    }


    private static long toLong(String contents, int offset, int length) {
        if (length <= 0 || contents.length() < offset + length) {
            throw new NumberFormatException(
                    "offset = " + offset+ ", length = " + length + ": " + contents);
        }

        boolean negative = false;
        int i = 0;
        long limit = -Long.MAX_VALUE;
        int digit;

        char firstChar = contents.charAt(offset);
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = Long.MIN_VALUE;
            }
            else if (firstChar != '+') {
                throw new NumberFormatException(contents);
            }
            i++;
            if (length == 1) { // Cannot have lone "+" or "-"
                throw new NumberFormatException(contents);
            }
        }

        long multmin = limit / 10;
        var result = 0L;
        while (i < length) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = contents.charAt(offset + i++) - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException(contents);
            }
            if (result < multmin) {
                throw new NumberFormatException(contents);
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(contents);
            }
            result -= digit;
        }

        return negative ? result : -result;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public static void toStringFromRight(long i, int endIndex, char[] chars) {
        long q;
        int r;
        int charPos = endIndex;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i <= Integer.MIN_VALUE) {
            q = i / 100;
            r = (int)((q * 100) - i);
            i = q;
            chars[charPos--] = digitOnes[r];
            chars[charPos--] = digitTens[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            r  = (q2 * 100) - i2;
            i2 = q2;
            chars[charPos--] = digitOnes[r];
            chars[charPos--] = digitTens[r];
        }

        // We know there are at most two digits left at this point.
        q2 = i2 / 10;
        r  = (q2 * 10) - i2;
        chars[charPos--] = (char)('0' + r);

        // Whatever left is the remaining digit.
        if (q2 < 0) {
            chars[charPos--] = (char)('0' - q2);
        }

        if (negative) {
            chars[charPos] = '-';
        }
    }


    // NB: shadowed from java.lang.Long, where it's not public
    public static int stringSize(long value) {
        int d = 1;
        if (value >= 0) {
            d = 0;
            value = -value;
        }
        long p = -10;
        for (int i = 1; i < 19; i++) {
            if (value > p) {
                return i + d;
            }
            p = 10 * p;
        }
        return 19 + d;
    }
}
