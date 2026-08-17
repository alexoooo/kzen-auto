package tech.kzen.auto.plugin.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterization suite: every assertion pins observed behaviour of the shipped parser, including the
 * points where it deliberately diverges from {@link Double#parseDouble} — that tolerance is the contract
 * the report pipeline relies on. Each case is driven through both public entry points, so a regression in
 * the FastDoubleParser integration (wrong overload, wrong offset handling) fails here even though the
 * vendored algorithm itself is upstream-owned and untested here.
 */
class NumberParseUtilsTest {
    private static final long positiveZeroBits = Double.doubleToRawLongBits(0.0);


    private static double parse(String text) {
        double fromCharSequence = NumberParseUtils.toDoubleOrNan(text);
        char[] chars = text.toCharArray();
        double fromCharArray = NumberParseUtils.toDoubleOrNan(chars, 0, chars.length, new long[2]);

        assertEquals(
                Double.doubleToLongBits(fromCharSequence),
                Double.doubleToLongBits(fromCharArray),
                "CharSequence and char[] entry points disagree for: " + text);

        return fromCharSequence;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void parsesSignedIntegersAndDecimals() {
        assertEquals(0.0, parse("0"));
        assertEquals(1.0, parse("1"));
        assertEquals(1.0, parse("+1"));
        assertEquals(-1.0, parse("-1"));
        assertEquals(0.1, parse("0.1"));
        assertEquals(-12.5, parse("-12.5"));
        assertEquals(0.5, parse(".5"));
        assertEquals(5.0, parse("5."));
        assertEquals(0.5, parse("00.5"));
    }


    @Test
    void normalizesNegativeZeroToPositiveZero() {
        assertEquals(-0.0, Double.parseDouble("-0.0"));

        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(parse("-0")));
        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(parse("-0.0")));
        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(parse("-0.000")));
    }


    @Test
    void normalizesNegativeUnderflowToPositiveZero() {
        assertEquals(-0.0, Double.parseDouble("-1e-400"));

        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(parse("-1e-400")));
        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(parse("1e-400")));
    }


    @Test
    void reportsInvalidInputAsNanInsteadOfThrowing() {
        assertEquals(Double.NaN, parse(""));
        assertEquals(Double.NaN, parse(" "));
        assertEquals(Double.NaN, parse("abc"));
        assertEquals(Double.NaN, parse("1abc"));
        assertEquals(Double.NaN, parse("--1"));
        assertEquals(Double.NaN, parse("1_000"));
        assertEquals(Double.NaN, parse("1,000"));
        assertEquals(Double.NaN, parse("e3"));
        assertEquals(Double.NaN, parse("1e"));
        assertEquals(Double.NaN, parse("1e+"));
    }


    @Test
    void rejectsJavaFloatAndDoubleTypeSuffixes() {
        assertEquals(1.0, Double.parseDouble("1d"));
        assertEquals(1.0, Double.parseDouble("1f"));

        assertEquals(Double.NaN, parse("1d"));
        assertEquals(Double.NaN, parse("1D"));
        assertEquals(Double.NaN, parse("1f"));
        assertEquals(Double.NaN, parse("1F"));
    }


    @Test
    void acceptsBareDecimalPointAsZero() {
        assertThrows(NumberFormatException.class, () -> Double.parseDouble("."));

        assertEquals(0.0, parse("."));
    }


    @Test
    void skipsSurroundingWhitespaceButRejectsInteriorGaps() {
        assertEquals(1.0, parse(" 1 "));
        assertEquals(1.0, parse("\t1\t"));
        assertEquals(1.0, parse("\n1\n"));
        assertEquals(Double.NaN, parse("1 2"));
    }


    @Test
    void parsesExponentForms() {
        assertEquals(1000.0, parse("1e3"));
        assertEquals(1000.0, parse("1E3"));
        assertEquals(1000.0, parse("1e+3"));
        assertEquals(0.001, parse("1e-3"));
        assertEquals(150.0, parse("1.5e2"));
    }


    @Test
    void parsesHexFloatOnlyWhenBinaryExponentPresent() {
        assertEquals(8.0, parse("0x1p3"));
        assertEquals(8.0, parse("0X1P3"));
        assertEquals(3.0, parse("0x1.8p1"));
        assertEquals(Double.NaN, parse("0x1"));
    }


    @Test
    void parsesInfinityOnlyInCanonicalSpelling() {
        assertEquals(Double.POSITIVE_INFINITY, parse("Infinity"));
        assertEquals(Double.POSITIVE_INFINITY, parse("+Infinity"));
        assertEquals(Double.NEGATIVE_INFINITY, parse("-Infinity"));
        assertEquals(Double.NaN, parse("Inf"));
        assertEquals(Double.NaN, parse("infinity"));
    }


    @Test
    void nanSpellingIsIndistinguishableFromParseFailure() {
        assertEquals(Double.NaN, parse("NaN"));
        assertEquals(Double.NaN, parse("nan"));
    }


    @Test
    void saturatesOutsideDoubleRange() {
        assertEquals(Double.POSITIVE_INFINITY, parse("1.8e308"));
        assertEquals(Double.NEGATIVE_INFINITY, parse("-1.8e308"));
        assertEquals(Double.MAX_VALUE, parse("1.7976931348623157E308"));
        assertEquals(Double.MIN_VALUE, parse("4.9e-324"));
    }


    @Test
    void matchesJavaOnDigitCountsBeyondTheFastPath() {
        String[] longDigitStrings = {
                "9223372036854775807",
                "9223372036854775808",
                "-9223372036854775808",
                "-9223372036854775809",
                "12345678901234567890123456789",
                "1.0000000000000000000000001",
                "000000000000000000000000001",
                "3.14159265358979323846"
        };

        for (String text : longDigitStrings) {
            assertEquals(Double.parseDouble(text), parse(text), "for: " + text);
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void parsesWindowInsideLargerBuffer() {
        String host = "xx-12.5yy";
        int numberOffset = 2;
        int numberLength = 5;

        assertEquals(-12.5, NumberParseUtils.toDoubleOrNan(host, numberOffset, numberLength, new long[2]));
        assertEquals(-12.5, NumberParseUtils.toDoubleOrNan(
                host.toCharArray(), numberOffset, numberLength, new long[2]));
    }


    @Test
    void emptyWindowIsNan() {
        String host = "xx-12.5yy";

        assertEquals(Double.NaN, NumberParseUtils.toDoubleOrNan(host, 2, 0, new long[2]));
        assertEquals(Double.NaN, NumberParseUtils.toDoubleOrNan(host.toCharArray(), 2, 0, new long[2]));
    }


    @Test
    void scratchArrayIsOptionalAndReusable() {
        long[] scratch = new long[2];

        assertEquals(1.5, NumberParseUtils.toDoubleOrNan("1.5", scratch));
        assertEquals(2.5, NumberParseUtils.toDoubleOrNan("2.5", scratch));
        assertEquals(2.5, NumberParseUtils.toDoubleOrNan("2.5"));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void decimalLongPowersCoverEveryExponentUpToSeventeen() {
        int expectedPowerCount = 18;
        assertEquals(expectedPowerCount, NumberParseUtils.decimalLongPowers.length);

        long expected = 1;
        for (int i = 0; i < NumberParseUtils.decimalLongPowers.length; i++) {
            assertEquals(expected, NumberParseUtils.decimalLongPowers[i], "at exponent " + i);
            expected *= 10;
        }
    }


    @Test
    void maxLongDecimalLengthIsEighteen() {
        assertEquals(18, NumberParseUtils.maxLongDecimalLength);
        assertTrue(Long.toString(Long.MAX_VALUE).length() > NumberParseUtils.maxLongDecimalLength);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void stringSizeMatchesLongToStringLength() {
        long[] samples = {
                0, 1, 9, 10, 99, 100, -1, -9, -10, -99,
                999999999999999999L, Long.MAX_VALUE, Long.MIN_VALUE
        };

        for (long value : samples) {
            assertEquals(
                    Long.toString(value).length(),
                    NumberParseUtils.stringSize(value),
                    "for: " + value);
        }
    }


    @Test
    void toStringFromRightWritesDigitsEndingAtGivenIndex() {
        long[] samples = {0, 1, -1, 10, -10, 999999999999999999L, Long.MAX_VALUE, Long.MIN_VALUE};

        for (long value : samples) {
            int size = NumberParseUtils.stringSize(value);
            char[] chars = new char[size];
            NumberParseUtils.toStringFromRight(value, size - 1, chars);
            assertEquals(Long.toString(value), new String(chars), "for: " + value);
        }
    }


    @Test
    void toStringFromRightLeavesPrecedingBufferPositionsUntouched() {
        char[] chars = "########".toCharArray();
        NumberParseUtils.toStringFromRight(42, chars.length - 1, chars);
        assertArrayEquals("######42".toCharArray(), chars);

        char[] negativeChars = "########".toCharArray();
        NumberParseUtils.toStringFromRight(-42, negativeChars.length - 1, negativeChars);
        assertArrayEquals("#####-42".toCharArray(), negativeChars);
    }
}
