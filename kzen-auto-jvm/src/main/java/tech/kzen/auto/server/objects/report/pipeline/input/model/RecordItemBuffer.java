package tech.kzen.auto.server.objects.report.pipeline.input.model;


import net.openhft.hashing.LongHashFunction;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.CsvRecordParser;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.NumberParseUtils;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.TsvRecordParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class RecordItemBuffer
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final double doubleCacheMissing = -0.0;
    private static final long missingNumberBits = Double.doubleToRawLongBits(doubleCacheMissing);

    private static boolean isDoubleCacheMissing(double value) {
        return Double.doubleToRawLongBits(value) == missingNumberBits;
    }


    public static RecordItemBuffer of(String... values) {
        return of(Arrays.asList(values));
    }


    public static RecordItemBuffer of(List<String> values) {
        RecordItemBuffer buffer = new RecordItemBuffer(0, 0);
        buffer.addAll(values);
        buffer.populateCaches();
        return buffer;
    }


    public static RecordItemBuffer ofSingle(char[] contents, int offset, int length) {
        RecordItemBuffer buffer = new RecordItemBuffer(length, 1);
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
    double[] doublesCache;
    long[] hashesCache;
    private int fieldCount = 0;
    private int fieldContentLength = 0;
    private boolean nonEmpty = false;


    //-----------------------------------------------------------------------------------------------------------------
    public RecordItemBuffer()
    {
        this(0, 0);
    }


    public RecordItemBuffer(int expectedContentLength, int expectedFieldCount)
    {
        fieldContents = new char[expectedContentLength];
        fieldEnds = new int[expectedFieldCount];
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
        int startIndex = start(fieldIndex);
        int length = fieldEnds[fieldIndex] - startIndex;
        return new String(fieldContents, startIndex, length);
    }


    public List<String> toList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++) {
            String item = getString(i);
            list.add(item);
        }
        return list;
    }


//    /**
//     * NB: must call parseDoubles ahead of time
//     * @param fieldIndex field index
//     * @return double value of field contents or NaN
//     */
//    public double cachedDoubleOrNan(int fieldIndex) {
//        return doublesCache[fieldIndex];
//    }


    public void populateCaches() {
        growCachesIfRequired(fieldCount);

        for (int i = 0; i < fieldCount; i++) {
            populateCache(i);
        }
    }


    private void growCachesIfRequired(int size) {
        if (doublesCache == null) {
            doublesCache = new double[fieldCount];
            Arrays.fill(doublesCache, doubleCacheMissing);

            hashesCache = new long[fieldCount];
        }
        else if (doublesCache.length < size) {
            int oldLength = doublesCache.length;
            doublesCache = Arrays.copyOf(doublesCache, size);
            Arrays.fill(doublesCache, oldLength, size, doubleCacheMissing);

            hashesCache = Arrays.copyOf(hashesCache, size);
        }
    }


    private void populateCache(int fieldIndex) {
        int start = start(fieldIndex);
        int length = fieldEnds[fieldIndex] - start;

        cacheDouble(fieldIndex, start, length);
        cacheHash(fieldIndex, start, length);
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
                out.write(CsvRecordParser.delimiterInt);
            }

            writeCsvField(i, out);
        }
    }


    public void writeCsvField(int fieldIndex, Writer out) throws IOException {
        int startIndex = start(fieldIndex);
        int endIndex = fieldEnds[fieldIndex];
        CsvRecordParser.writeCsv(fieldContents, startIndex, endIndex, out);
    }


    public String toTsv() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeTsv(writer);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }


    private void writeTsv(Writer out) throws IOException {
        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            throw new IllegalStateException("Can't represent non-empty record with single empty column");
        }

        for (int i = 0; i < fieldCount; i++) {
            if (i != 0) {
                out.write(TsvRecordParser.delimiterInt);
            }

            writeTsvField(i, out);
        }
    }


    private void writeTsvField(int fieldIndex, Writer out) throws IOException {
        int startIndex = start(fieldIndex);
        int length = fieldEnds[fieldIndex] - startIndex;
        out.write(fieldContents, startIndex, length);
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
        growFieldEndsIfRequired(fieldCount);

        fieldEnds[fieldCount] = fieldContentLength;
        fieldCount++;
    }


    public void addToFieldAndCommit(char[] chars, int offset, int length) {
        if (length != 0) {
            if (fieldContents.length < fieldContentLength + length) {
                int nextSize = Math.max((int) (fieldContents.length * 1.2), fieldContentLength + length);
                fieldContents = Arrays.copyOf(fieldContents, nextSize);
            }
            System.arraycopy(chars, offset, fieldContents, fieldContentLength, length);
            fieldContentLength += length;
        }

        if (fieldEnds.length <= fieldCount) {
            int nextSize = Math.max((int) (fieldEnds.length * 1.2 + 1), fieldCount);
            fieldEnds = Arrays.copyOf(fieldEnds, nextSize);
        }
        fieldEnds[fieldCount] = fieldContentLength;
        fieldCount++;
    }


    public void add(String value) {
        growFieldContentsIfRequired(fieldContentLength + value.length());

        // see: https://stackoverflow.com/questions/8894258/fastest-way-to-iterate-over-all-the-chars-in-a-string
        for (int i = 0; i < value.length(); i++) {
            fieldContents[fieldContentLength++] = value.charAt(i);
        }

        commitField();
    }


    public void addAll(List<String> values) {
        for (String value : values) {
            add(value);
        }
    }


    public void addAllAndPopulateCaches(String[] values) {
        growCachesIfRequired(fieldCount + values.length);

        for (String value : values) {
            add(value);
            populateCache(fieldCount - 1);
        }
    }


    public void clear() {
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
    public void copy(RecordItemBuffer that) {
        fieldCount = that.fieldCount;
        fieldContentLength = that.fieldContentLength;
        nonEmpty = that.nonEmpty;

        growFieldContentsIfRequired(fieldContentLength);
        growFieldEndsIfRequired(fieldCount);

        System.arraycopy(that.fieldContents, 0, fieldContents, 0, fieldContentLength);
        System.arraycopy(that.fieldEnds, 0, fieldEnds, 0, fieldCount);

        if (that.doublesCache != null) {
            if (doublesCache == null || doublesCache.length < fieldCount) {
                doublesCache = new double[fieldCount];
                hashesCache = new long[fieldCount];
            }
            System.arraycopy(that.doublesCache, 0, doublesCache, 0, fieldCount);
            System.arraycopy(that.hashesCache, 0, hashesCache, 0, fieldCount);
        }
    }


    public RecordItemBuffer prototype() {
        RecordItemBuffer prototype = new RecordItemBuffer(0, 0);
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
        growFieldEndsIfRequired(fieldCount + additionalFieldCount);
    }


    private void growFieldContentsIfRequired(int required) {
        if (fieldContents.length < required) {
            int nextSize = Math.max((int) (fieldContents.length * 1.2), required);
            fieldContents = Arrays.copyOf(fieldContents, nextSize);
        }
    }


    private void growFieldEndsIfRequired(int required) {
        if (fieldEnds.length <= required) {
            int nextSize = Math.max((int) (fieldEnds.length * 1.2 + 1), required);
            fieldEnds = Arrays.copyOf(fieldEnds, nextSize);
        }
    }


    int start(int fieldIndex) {
        return fieldIndex == 0 ? 0 : fieldEnds[fieldIndex - 1];
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return toCsv();
    }
}
