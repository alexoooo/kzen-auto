package tech.kzen.auto.plugin.util;


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
    public static double toDoubleOrNan(char[] contents, int offset, int length, long[] i128) {
        double value = FastDoubleParserFromCharArray.parseDoubleOrNan(contents, offset, length, i128);
        return normalizeNegativeZero(value);
    }


    public static double toDoubleOrNan(String contents) {
        return toDoubleOrNan(contents, 0, contents.length(), null);
    }

    public static double toDoubleOrNan(String contents, long[] i128) {
        return toDoubleOrNan(contents, 0, contents.length(), i128);
    }


    public static double toDoubleOrNan(String contents, int offset, int length, long[] i128) {
        double value = FastDoubleParserFromCharSequence.parseDoubleOrNan(contents, offset, length, i128);
        return normalizeNegativeZero(value);
    }


    private static double normalizeNegativeZero(double value) {
        return value == -0.0 ? 0 : value;
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
