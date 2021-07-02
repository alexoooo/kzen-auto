package tech.kzen.auto.plugin.model.record;


import net.openhft.hashing.LongHashFunction;
import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.util.NumberParseUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FlatFileRecord
        //implements FlatRecordBuilder
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final double doubleCacheMissing = -0.0;
    private static final long missingNumberBits = Double.doubleToRawLongBits(doubleCacheMissing);

    private static boolean isDoubleCacheMissing(double value) {
        return Double.doubleToRawLongBits(value) == missingNumberBits;
    }

//    public static final ClassName className = new ClassName(FlatFileRecord.class.getName());


    public static FlatFileRecord of(String... values) {
        return of(Arrays.asList(values));
    }


    public static FlatFileRecord of(List<String> values) {
        FlatFileRecord buffer = new FlatFileRecord(0, 0);
        buffer.addAll(values);
        buffer.populateCaches();
        return buffer;
    }


    public static FlatFileRecord ofSingle(char[] contents, int offset, int length) {
        FlatFileRecord buffer = new FlatFileRecord(length, 1);
        System.arraycopy(contents, offset, buffer.fieldContents, 0, length);
        buffer.fieldCount = 1;
        buffer.fieldEnds[0] = length;
        buffer.fieldContentLength = length;
        buffer.nonEmpty = true;
        buffer.populateCaches();
        return buffer;
    }


    //-----------------------------------------------------------------------------------------------------------------
    char[] fieldContents;
    int[] fieldEnds;

    private boolean hasCache;
    private double[] doublesCache;
    private long[] hashesCache;

    private int fieldCount = 0;
    private int fieldContentLength = 0;

    // TODO: is this necessary?
    private boolean nonEmpty = false;


    //-----------------------------------------------------------------------------------------------------------------
    public FlatFileRecord()
    {
        this(0, 0);
    }


    public FlatFileRecord(int expectedContentLength, int expectedFieldCount)
    {
        fieldContents = new char[expectedContentLength];
        fieldEnds = new int[expectedFieldCount];

        doublesCache = new double[expectedFieldCount];
        hashesCache = new long[expectedFieldCount];
        Arrays.fill(doublesCache, 0, expectedFieldCount, doubleCacheMissing);
    }


    //-----------------------------------------------------------------------------------------------------------------
    public int fieldCount() {
        return fieldCount;
    }


    public int fieldContentLength() {
        return fieldContentLength;
    }


    public boolean isEmpty() {
        return ! nonEmpty &&
                fieldCount <= 1 &&
                fieldContentLength == 0;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public String getString(int fieldIndex) {
        int startIndex = contentStart(fieldIndex);
        int length = fieldEnds[fieldIndex] - startIndex;
        return new String(fieldContents, startIndex, length);
    }


    public List<String> toList() {
        List<String> builder = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            String item = getString(i);
            builder.add(item);
        }
        return builder;
    }


    public boolean isCached(int fieldIndex) {
        return ! isDoubleCacheMissing(doublesCache[fieldIndex]);
    }


    /**
     * @param fieldIndex field index
     * @return double value of field contents or NaN
     */
    public double cachedDoubleOrNan(int fieldIndex) {
        double cached = doublesCache[fieldIndex];
        if (isDoubleCacheMissing(cached)) {
            populateCache(fieldIndex);
            return doublesCache[fieldIndex];
        }
        return cached;
    }


    public long cachedHash(int fieldIndex) {
        populateCacheIfRequired(fieldIndex);
        return hashesCache[fieldIndex];
    }


    public void populateCaches() {
        for (int i = 0; i < fieldCount; i++) {
            populateCacheIfRequired(i);
        }
    }


    private void populateCacheIfRequired(int fieldIndex) {
        if (! isDoubleCacheMissing(doublesCache[fieldIndex])) {
            return;
        }

        int start = contentStart(fieldIndex);
        int length = fieldEnds[fieldIndex] - start;

        cacheDouble(fieldIndex, start, length);
        cacheHash(fieldIndex, start, length);
        hasCache = true;
    }


    private void populateCache(int fieldIndex) {
        int start = contentStart(fieldIndex);
        int length = fieldEnds[fieldIndex] - start;

        cacheDouble(fieldIndex, start, length);
        cacheHash(fieldIndex, start, length);
        hasCache = true;
    }


    private void cacheDouble(int fieldIndex, int start, int length) {
        int startCursor = start;
        int lengthCursor = length;
        for (int i = 0; i < lengthCursor; i++) {
            if (fieldContents[startCursor] != ' ') {
                break;
            }
            startCursor++;
            lengthCursor--;
        }

        for (int i = (lengthCursor - 1); i >= 0; i--) {
            if (fieldContents[startCursor + i] != ' ') {
                break;
            }
            lengthCursor--;
        }

        double value = NumberParseUtils.toDoubleOrNan(fieldContents, startCursor, lengthCursor);
        doublesCache[fieldIndex] = value;
    }


    private void cacheHash(int fieldIndex, int start, int length) {
        long value = LongHashFunction.murmur_3().hashChars(fieldContents, start, length);
        hashesCache[fieldIndex] = value;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public String toCsv() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeCsv(writer);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }


    public void writeCsv(Writer out) throws IOException {
        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            out.write("\"\"");
            return;
        }

        for (int i = 0; i < fieldCount; i++) {
            if (i != 0) {
                out.write(',');
            }

            writeCsvField(i, out);
        }
    }


    public void writeCsvField(int fieldIndex, Writer out) throws IOException {
        int startIndex = contentStart(fieldIndex);
        int endIndex = fieldEnds[fieldIndex];

        //CsvFormatUtils.writeCsv(fieldContents, startIndex, endIndex, out);
        var containsSpecial = false;
        for (int i = startIndex; i < endIndex; i++) {
            char nextChar = fieldContents[i];
            if (nextChar == ',' || nextChar == '"' || nextChar == '\r' || nextChar == '\n') {
                containsSpecial = true;
                break;
            }
        }

        if (containsSpecial) {
            out.write('"');
            for (int i = startIndex; i < endIndex; i++) {
                char nextChar = fieldContents[i];
                if (nextChar == '"') {
                    out.write('"');
                    out.write('"');
                }
                else {
                    out.write(nextChar);
                }
            }
            out.write('"');
        }
        else {
            out.write(fieldContents, startIndex, endIndex - startIndex);
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void indicateNonEmpty() {
        nonEmpty = true;
    }


    public void addToField(char nextChar) {
        growFieldContentsIfRequired(fieldContentLength + 1);
        fieldContents[fieldContentLength] = nextChar;
        fieldContentLength++;
    }


    public void addToField(char[] chars, int offset, int length) {
        if (length != 0) {
            growFieldContentsIfRequired(fieldContentLength + length);
            System.arraycopy(chars, offset, fieldContents, fieldContentLength, length);
            fieldContentLength += length;
        }
    }


    public void commitField() {
        growFieldEndsIfRequired(fieldCount + 1);

        fieldEnds[fieldCount] = fieldContentLength;
        fieldCount++;
    }


//    @Override
    public void add(CharSequence value) {
        growFieldContentsIfRequired(fieldContentLength + value.length());

        // see: https://stackoverflow.com/questions/8894258/fastest-way-to-iterate-over-all-the-chars-in-a-string
        for (int i = 0; i < value.length(); i++) {
            fieldContents[fieldContentLength++] = value.charAt(i);
        }

        commitField();
    }


//    @Override
    public void addAll(List<String> values) {
        for (String value : values) {
            add(value);
        }
    }


    public void addAll(String[] values) {
        for (String value : values) {
            add(value);
        }
    }


//    @Override
    public void add(long value) {
        int length = NumberParseUtils.stringSize(value);
        int requiredContentLength = fieldContentLength + length;

        growFieldContentsIfRequired(requiredContentLength);
        NumberParseUtils.toStringFromRight(value, requiredContentLength - 1, fieldContents);

        fieldContentLength = requiredContentLength;
        commitField();
    }


//    @Override
    public void add(double value, int decimalPlaces) {
        if (decimalPlaces == 0) {
            add(Math.round(value));
            return;
        }

        double absolute = Math.abs(value);
        long wholeValue = (long) absolute;
        double factionValue = absolute - wholeValue;
        boolean negative = value < 0.0;

        long fractionFactor = NumberParseUtils.decimalLongPowers[decimalPlaces];
        long fractionLong = Math.round(factionValue * fractionFactor);

        if (fractionFactor == fractionLong) {
            long adjustedWhole = (negative ? -1 : 1) * (wholeValue + 1);

            int length = NumberParseUtils.stringSize(adjustedWhole);
            int requiredContentLength = fieldContentLength + length + decimalPlaces + 1;
            growFieldContentsIfRequired(requiredContentLength);
            NumberParseUtils.toStringFromRight(adjustedWhole, fieldContentLength + length - 1, fieldContents);

            fieldContents[fieldContentLength + length] = '.';
            for (int i = 1; i <= decimalPlaces; i++) {
                fieldContents[fieldContentLength + length + i] = '0';
            }

            fieldContentLength = requiredContentLength;
            commitField();
            return;
        }

        int wholeLength = NumberParseUtils.stringSize(wholeValue);
        int length = wholeLength + decimalPlaces + 1;

        int minusLength = (negative ? 1 : 0);

        int requiredContentLength = fieldContentLength + length + minusLength;
        growFieldContentsIfRequired(requiredContentLength);

        int endOfWhole = fieldContentLength + wholeLength + minusLength - 1;
        NumberParseUtils.toStringFromRight(wholeValue, endOfWhole, fieldContents);

        fieldContents[endOfWhole + 1] = '.';

        NumberParseUtils.toStringFromRight(fractionLong, requiredContentLength - 1, fieldContents);

        int fractionLength = NumberParseUtils.stringSize(fractionLong);
        int fractionLeadingZeroes = decimalPlaces - fractionLength;
        for (int i = 0; i < fractionLeadingZeroes; i++) {
            fieldContents[endOfWhole + i + 2] = '0';
        }

        if (negative) {
            fieldContents[fieldContentLength] = '-';
        }

        fieldContentLength = requiredContentLength;
        commitField();
    }


//    @Override
    public void add(@NotNull char[] value, int offset, int length) {
        int requiredContentLength = fieldContentLength + length;
        growFieldContentsIfRequired(requiredContentLength);

        System.arraycopy(value, offset, fieldContents, fieldContentLength, length);

        fieldContentLength = requiredContentLength;
        commitField();
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void clear() {
        if (hasCache) {
            Arrays.fill(doublesCache, 0, fieldCount, doubleCacheMissing);
            hasCache = false;
        }

        fieldCount = 0;
        fieldContentLength = 0;
        nonEmpty = false;
    }


    public void clearCache() {
        if (hasCache) {
            Arrays.fill(doublesCache, 0, fieldCount, doubleCacheMissing);
            hasCache = false;
        }
    }


    public void clearWithoutCache() {
        fieldCount = 0;
        fieldContentLength = 0;
        nonEmpty = false;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public char[] fieldContentsUnsafe() {
        return fieldContents;
    }


    public int[] fieldEndsUnsafe() {
        return fieldEnds;
    }


    public void setCountAndLengthUnsafe(int fieldCount, int fieldContentLength) {
        this.fieldCount = fieldCount;
        this.fieldContentLength = fieldContentLength;
    }


    public void addToFieldUnsafe(char nextChar) {
        fieldContents[fieldContentLength] = nextChar;
        fieldContentLength++;
    }


    public void addToFieldUnsafe(char[] chars, int offset, int length) {
        System.arraycopy(chars, offset, fieldContents, fieldContentLength, length);
        fieldContentLength += length;
    }


    public void commitFieldUnsafe() {
        fieldEnds[fieldCount] = fieldContentLength;
        fieldCount++;
    }


    public void addToFieldAndCommitUnsafe(char[] chars, int offset, int length) {
        System.arraycopy(chars, offset, fieldContents, fieldContentLength, length);
        fieldContentLength += length;

        fieldEnds[fieldCount] = fieldContentLength;
        fieldCount++;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void copy(FlatFileRecord that) {
        int previousFieldCount = fieldCount;

        fieldCount = that.fieldCount;
        fieldContentLength = that.fieldContentLength;
        nonEmpty = that.nonEmpty;

        growFieldContentsIfRequired(fieldContentLength);
        growFieldEndsIfRequired(fieldCount);

        System.arraycopy(that.fieldContents, 0, fieldContents, 0, fieldContentLength);
        System.arraycopy(that.fieldEnds, 0, fieldEnds, 0, fieldCount);

        if (hasCache) {
            int maxFieldCount = Math.max(previousFieldCount, fieldCount);
            Arrays.fill(doublesCache, 0, maxFieldCount, doubleCacheMissing);
            hasCache = false;
        }
    }


    public void exchange(FlatFileRecord that) {
        char[] tempFieldContents = that.fieldContents;
        int[] tempFieldEnds = that.fieldEnds;
        boolean tempHasCache = that.hasCache;
        double[] tempDoublesCache = that.doublesCache;
        long[] tempHashesCache = that.hashesCache;
        int tempFieldCount = that.fieldCount;
        int tempFieldContentLength = that.fieldContentLength;
        boolean tempNonEmpty = that.nonEmpty;

        that.fieldContents = fieldContents;
        that.fieldEnds = fieldEnds;
        that.hasCache = hasCache;
        that.doublesCache = doublesCache;
        that.hashesCache = hashesCache;
        that.fieldCount = fieldCount;
        that.fieldContentLength = fieldContentLength;
        that.nonEmpty = nonEmpty;

        fieldContents = tempFieldContents;
        fieldEnds = tempFieldEnds;
        hasCache = tempHasCache;
        doublesCache = tempDoublesCache;
        hashesCache = tempHashesCache;
        fieldCount = tempFieldCount;
        fieldContentLength = tempFieldContentLength;
        nonEmpty = tempNonEmpty;
    }


    public void clone(FlatFileRecord that) {
        fieldCount = that.fieldCount;
        fieldContentLength = that.fieldContentLength;
        nonEmpty = that.nonEmpty;

        fieldContents = that.fieldContents;
        fieldEnds = that.fieldEnds;

        hasCache = that.hasCache;
        doublesCache = that.doublesCache;
        hashesCache = that.hashesCache;
    }


    public FlatFileRecord prototype() {
        FlatFileRecord prototype = new FlatFileRecord(0, 0);
        prototype.copy(this);
        return prototype;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void growTo(int requiredLength, int requiredFieldCount) {
        growFieldContentsIfRequired(requiredLength);
        growFieldEndsIfRequired(requiredFieldCount);
    }


    public void growBy(int additionalLength, int additionalFieldCount) {
        growFieldContentsIfRequired(fieldContentLength + additionalLength);
        growFieldEndsIfRequired(fieldCount + additionalFieldCount + 1);
    }


    private void growFieldContentsIfRequired(int required) {
        if (fieldContents.length < required) {
            int nextSize = Math.max((int) (fieldContents.length * 1.2), required);
            fieldContents = Arrays.copyOf(fieldContents, nextSize);
        }
    }


    private void growFieldEndsIfRequired(int required) {
        if (fieldEnds.length < required) {
            int size = fieldEnds.length;
            int nextSize = Math.max((int) (size * 1.2 + 1), required);
            fieldEnds = Arrays.copyOf(fieldEnds, nextSize);

            doublesCache = Arrays.copyOf(doublesCache, nextSize);
            hashesCache = Arrays.copyOf(hashesCache, nextSize);
            Arrays.fill(doublesCache, size, nextSize, doubleCacheMissing);
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    public int contentEnd(int fieldIndex) {
        return fieldEnds[fieldIndex];
    }


    public int contentStart(int fieldIndex) {
        return fieldIndex == 0 ? 0 : fieldEnds[fieldIndex - 1];
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return toCsv();
    }
}
