package tech.kzen.auto.plugin.util;


// see: https://github.com/wrandelshofer/FastDoubleParser
class FastDoubleParserFromCharSequence {
    private final static long MINIMAL_NINETEEN_DIGIT_INTEGER = 1000_00000_00000_00000L;
    private final static int MINIMAL_EIGHT_DIGIT_INTEGER = 10_000_000;
    /**
     * Special value in {@link #CHAR_TO_HEX_MAP} for
     * the decimal point character.
     */
    private static final byte DECIMAL_POINT_CLASS = -4;
    /**
     * Special value in {@link #CHAR_TO_HEX_MAP} for
     * characters that are neither a hex digit nor
     * a decimal point character..
     */
    private static final byte OTHER_CLASS = -1;
    /**
     * A table of 128 entries or of entries up to including
     * character 'p' would suffice.
     * <p>
     * However for some reason, performance is best,
     * if this table has exactly 256 entries.
     */
    private static final byte[] CHAR_TO_HEX_MAP = new byte[256];

    static {
        for (char ch = 0; ch < CHAR_TO_HEX_MAP.length; ch++) {
            CHAR_TO_HEX_MAP[ch] = OTHER_CLASS;
        }
        for (char ch = '0'; ch <= '9'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - '0');
        }
        for (char ch = 'A'; ch <= 'F'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'A' + 10);
        }
        for (char ch = 'a'; ch <= 'f'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'a' + 10);
        }
        for (char ch = '.'; ch <= '.'; ch++) {
            CHAR_TO_HEX_MAP[ch] = DECIMAL_POINT_CLASS;
        }
    }

    /**
     * Prevents instantiation.
     */
    private FastDoubleParserFromCharSequence() {}


    private static boolean isInteger(char c) {
        return '0' <= c && c <= '9';
    }

//    private static NumberFormatException newNumberFormatException(CharSequence str) {
//        if (str.length() > 1024) {
//            // str can be up to Integer.MAX_VALUE characters long
//            return new NumberFormatException("For input string of length " + str.length());
//        } else {
//            return new NumberFormatException("For input string: \"" + str.toString().trim() + "\"");
//        }
//    }


    public static double parseDoubleOrNan(CharSequence str, int offset, int length, long[] i128) {
//        final int endIndex = str.length();
        final int endIndex = offset + length;

        // Skip leading whitespace
        // -------------------
        int index = skipWhitespace(str, offset, endIndex);
        if (index == endIndex) {
//            throw new NumberFormatException("empty String");
            return Double.NaN;
        }
        char ch = str.charAt(index);

        // Parse optional sign
        // -------------------
        final boolean isNegative = ch == '-';
        if (isNegative || ch == '+') {
            ch = ++index < endIndex ? str.charAt(index) : 0;
            if (ch == 0) {
//                throw newNumberFormatException(str);
                return Double.NaN;
            }
        }

        // Parse NaN or Infinity
        // ---------------------
        if (ch == 'N') {
//            return parseNaN(str, index, endIndex);
            return Double.NaN;
        } else if (ch == 'I') {
            return parseInfinity(str, index, endIndex, isNegative);
        }

        // Parse optional leading zero
        // ---------------------------
        final boolean hasLeadingZero = ch == '0';
        if (hasLeadingZero) {
            ch = ++index < endIndex ? str.charAt(index) : 0;
            if (ch == 'x' || ch == 'X') {
                return parseRestOfHexFloatingPointLiteral(str, index + 1, endIndex, isNegative, offset);
            }
        }

        return parseRestOfDecimalFloatLiteral(str, endIndex, index, isNegative, hasLeadingZero, offset, i128);
    }

    private static double parseInfinity(CharSequence str, int index, int endIndex, boolean negative) {
        if (index + 7 < endIndex
                //  && str.charAt(index) == 'I'
                && str.charAt(index + 1) == 'n'
                && str.charAt(index + 2) == 'f'
                && str.charAt(index + 3) == 'i'
                && str.charAt(index + 4) == 'n'
                && str.charAt(index + 5) == 'i'
                && str.charAt(index + 6) == 't'
                && str.charAt(index + 7) == 'y'
        ) {
            index = skipWhitespace(str, index + 8, endIndex);
            if (index < endIndex) {
//                throw newNumberFormatException(str);
                return Double.NaN;
            }
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        } else {
//            throw newNumberFormatException(str);
            return Double.NaN;
        }
    }

//    private static double parseNaN(CharSequence str, int index, int endIndex) {
//        if (index + 2 < endIndex
//                //   && str.charAt(index) == 'N'
//                && str.charAt(index + 1) == 'a'
//                && str.charAt(index + 2) == 'N') {
//
//            index = skipWhitespace(str, index + 3, endIndex);
//            if (index < endIndex) {
////                throw newNumberFormatException(str);
//                return Double.NaN;
//            }
//
//            return Double.NaN;
//        } else {
////            throw newNumberFormatException(str);
//            return Double.NaN;
//        }
//    }

    /**
     * Parses the following rules
     * (more rules are defined in {@link #parseDoubleOrNan}):
     * <dl>
     * <dt><i>RestOfDecimalFloatingPointLiteral</i>:
     * <dd><i>[Digits] {@code .} [Digits] [ExponentPart]</i>
     * <dd><i>{@code .} Digits [ExponentPart]</i>
     * <dd><i>[Digits] ExponentPart</i>
     * </dl>
     *
     * @param str            the input string
     * @param endIndex       the length of the string
     * @param index          index to the first character of RestOfHexFloatingPointLiteral
     * @param isNegative     if the resulting number is negative
     * @param hasLeadingZero if the digit '0' has been consumed
     * @return a double representation
     */
    private static double parseRestOfDecimalFloatLiteral(
            CharSequence str, int endIndex, int index, boolean isNegative, boolean hasLeadingZero, int offset, long[] i128
    ) {
        // Parse digits
        // ------------
        // Note: a multiplication by a constant is cheaper than an
        //       arbitrary integer multiplication.
        long digits = 0;// digits is treated as an unsigned long
        int exponent = 0;
        final int indexOfFirstDigit = index;
        int virtualIndexOfPoint = -1;
        final int digitCount;
        char ch = 0;
        for (; index < endIndex; index++) {
            ch = str.charAt(index);
            if (isInteger(ch)) {
                // This might overflow, we deal with it later.
                digits = 10 * digits + ch - '0';
            } else if (ch == '.') {
                if (virtualIndexOfPoint != -1) {
//                    throw newNumberFormatException(str);
                    return Double.NaN;
                }
                virtualIndexOfPoint = index;
            } else {
                break;
            }
        }
        final int indexAfterDigits = index;
        if (virtualIndexOfPoint == -1) {
            digitCount = indexAfterDigits - indexOfFirstDigit;
            virtualIndexOfPoint = indexAfterDigits;
        } else {
            digitCount = indexAfterDigits - indexOfFirstDigit - 1;
            exponent = virtualIndexOfPoint - index + 1;
        }

        // Parse exponent number
        // ---------------------
        long exp_number = 0;
        final boolean hasExponent = (ch == 'e') || (ch == 'E');
        if (hasExponent) {
            ch = ++index < endIndex ? str.charAt(index) : 0;
            boolean neg_exp = ch == '-';
            if (neg_exp || ch == '+') {
                ch = ++index < endIndex ? str.charAt(index) : 0;
            }
            if (!isInteger(ch)) {
//                throw newNumberFormatException(str);
                return Double.NaN;
            }
            do {
                // Guard against overflow of exp_number
                if (exp_number < MINIMAL_EIGHT_DIGIT_INTEGER) {
                    exp_number = 10 * exp_number + ch - '0';
                }
                ch = ++index < endIndex ? str.charAt(index) : 0;
            } while (isInteger(ch));
            if (neg_exp) {
                exp_number = -exp_number;
            }
            exponent += exp_number;
        }

        // Skip trailing whitespace
        // ------------------------
        index = skipWhitespace(str, index, endIndex);
        if (index < endIndex
                || !hasLeadingZero && digitCount == 0 && str.charAt(virtualIndexOfPoint) != '.') {
//            throw newNumberFormatException(str);
            return Double.NaN;
        }

        // Re-parse digits in case of a potential overflow
        // -----------------------------------------------
        final boolean isDigitsTruncated;
        int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
        if (digitCount > 19) {
            digits = 0;
            for (index = indexOfFirstDigit; index < indexAfterDigits; index++) {
                ch = str.charAt(index);
                if (ch == '.') {
                    skipCountInTruncatedDigits++;
                } else {
                    if (Long.compareUnsigned(digits, MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                        digits = 10 * digits + ch - '0';
                    } else {
                        break;
                    }
                }
            }
            isDigitsTruncated = (index < indexAfterDigits);
        } else {
            isDigitsTruncated = false;
        }
        double result = FastDoubleMath.decFloatLiteralToDouble(
                index, isNegative, digits, exponent, virtualIndexOfPoint, exp_number, isDigitsTruncated, skipCountInTruncatedDigits, i128);
        return Double.isNaN(result) ? parseRestOfDecimalFloatLiteralTheHardWay(str, offset, endIndex) : result;
    }

    /**
     * Parses the following rules
     * (more rules are defined in {@link #parseDoubleOrNan}):
     * <dl>
     * <dt><i>RestOfDecimalFloatingPointLiteral</i>:
     * <dd><i>[Digits] {@code .} [Digits] [ExponentPart]</i>
     * <dd><i>{@code .} Digits [ExponentPart]</i>
     * <dd><i>[Digits] ExponentPart</i>
     * </dl>
     *  @param str            the input string
     */
    private static double parseRestOfDecimalFloatLiteralTheHardWay(CharSequence str, int index, int endIndex) {
        try {
            return Double.parseDouble(str.subSequence(index, endIndex).toString());
        }
        catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Parses the following rules
     * (more rules are defined in {@link #parseDoubleOrNan}):
     * <dl>
     * <dt><i>RestOfHexFloatingPointLiteral</i>:
     * <dd><i>RestOfHexSignificand BinaryExponent</i>
     * </dl>
     *
     * <dl>
     * <dt><i>RestOfHexSignificand:</i>
     * <dd><i>HexDigits</i>
     * <dd><i>HexDigits</i> {@code .}
     * <dd><i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * </dl>
     *
     * @param str        the input string
     * @param index      index to the first character of RestOfHexFloatingPointLiteral
     * @param endIndex   the end index of the string
     * @param isNegative if the resulting number is negative
     * @return a double representation
     */
    private static double parseRestOfHexFloatingPointLiteral(
            CharSequence str, int index, int endIndex, boolean isNegative, int offset) {
        if (index >= endIndex) {
//            throw newNumberFormatException(str);
            return Double.NaN;
        }

        // Parse digits
        // ------------
        long digits = 0;// digits is treated as an unsigned long
        int exponent = 0;
        final int indexOfFirstDigit = index;
        int virtualIndexOfPoint = -1;
        final int digitCount;
        char ch = 0;
        for (; index < endIndex; index++) {
            ch = str.charAt(index);
            // Table look up is faster than a sequence of if-else-branches.
            int hexValue = ch > 255 ? OTHER_CLASS : CHAR_TO_HEX_MAP[ch];
            if (hexValue >= 0) {
                digits = (digits << 4) | hexValue;// This might overflow, we deal with it later.
            } else if (hexValue == DECIMAL_POINT_CLASS) {
                if (virtualIndexOfPoint != -1) {
//                    throw newNumberFormatException(str);
                    return Double.NaN;
                }
                virtualIndexOfPoint = index;
            } else {
                break;
            }
        }
        final int indexAfterDigits = index;
        if (virtualIndexOfPoint == -1) {
            digitCount = indexAfterDigits - indexOfFirstDigit;
            virtualIndexOfPoint = indexAfterDigits;
        } else {
            digitCount = indexAfterDigits - indexOfFirstDigit - 1;
            exponent = Math.min(virtualIndexOfPoint - index + 1, MINIMAL_EIGHT_DIGIT_INTEGER) * 4;
        }

        // Parse exponent number
        // ---------------------
        long exp_number = 0;
        final boolean hasExponent = (ch == 'p') || (ch == 'P');
        if (hasExponent) {
            ch = ++index < endIndex ? str.charAt(index) : 0;
            boolean neg_exp = ch == '-';
            if (neg_exp || ch == '+') {
                ch = ++index < endIndex ? str.charAt(index) : 0;
            }
            if (!isInteger(ch)) {
//                throw newNumberFormatException(str);
                return Double.NaN;
            }
            do {
                // Guard against overflow of exp_number
                if (exp_number < MINIMAL_EIGHT_DIGIT_INTEGER) {
                    exp_number = 10 * exp_number + ch - '0';
                }
                ch = ++index < endIndex ? str.charAt(index) : 0;
            } while (isInteger(ch));
            if (neg_exp) {
                exp_number = -exp_number;
            }
            exponent += exp_number;
        }

        // Skip trailing whitespace
        // ------------------------
        index = skipWhitespace(str, index, endIndex);
        if (index < endIndex
                || digitCount == 0 && str.charAt(virtualIndexOfPoint) != '.'
                || !hasExponent) {
//            throw newNumberFormatException(str);
            return Double.NaN;
        }

        // Re-parse digits in case of a potential overflow
        // -----------------------------------------------
        final boolean isDigitsTruncated;
        int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
        if (digitCount > 16) {
            digits = 0;
            for (index = indexOfFirstDigit; index < indexAfterDigits; index++) {
                ch = str.charAt(index);
                // Table look up is faster than a sequence of if-else-branches.
                int hexValue = ch > 127 ? OTHER_CLASS : CHAR_TO_HEX_MAP[ch];
                if (hexValue >= 0) {
                    if (Long.compareUnsigned(digits, MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                        digits = (digits << 4) | hexValue;
                    } else {
                        break;
                    }
                } else {
                    skipCountInTruncatedDigits++;
                }
            }
            isDigitsTruncated = (index < indexAfterDigits);
        } else {
            isDigitsTruncated = false;
        }

        double d = FastDoubleMath.hexFloatLiteralToDouble(
                index, isNegative, digits, exponent, virtualIndexOfPoint, exp_number, isDigitsTruncated, skipCountInTruncatedDigits);
        return Double.isNaN(d) ? parseRestOfDecimalFloatLiteralTheHardWay(str, offset, endIndex) : d;
    }

    private static int skipWhitespace(CharSequence str, int index, int endIndex) {
        for (; index < endIndex; index++) {
            if (str.charAt(index) > 0x20) {
                break;
            }
        }
        return index;
    }
}
