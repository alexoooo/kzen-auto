package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser;

import java.io.IOException;
import java.io.Writer;


public class CsvRecordParser implements RecordParser
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final int stateStartOfField = 0;
    private static final int stateInQuoted = 1;
    private static final int stateInQuotedQuote = 2;
    private static final int stateInUnquoted = 3;
    private static final int stateEndOfRecord = 4;

    private static final char quotation = '"';
    private static final char delimiter = ',';
    private static final char carriageReturn = '\r';
    private static final char lineFeed = '\n';

    public static final int delimiterInt = ',';


    //-----------------------------------------------------------------------------------------------------------------
    public static boolean isSpecial(char content) {
        return content == quotation ||
                content == delimiter ||
                content < ' ' ||
                content > '~';
    }


    public static void writeCsv(
            char[] contents,
            int startIndex,
            int endIndex,
            Writer out
    ) throws IOException {
        var containsSpecial = false;
        for (int i = startIndex; i < endIndex; i++) {
            char nextChar = contents[i];
            if (isSpecial(nextChar)) {
                containsSpecial = true;
                break;
            }
        }

        if (containsSpecial) {
            out.write(quotation);
            for (int i = startIndex; i < endIndex; i++) {
                char nextChar = contents[i];
                if (nextChar == quotation) {
                    out.write(quotation);
                    out.write(quotation);
                }
                else {
                    out.write(nextChar);
                }
            }
            out.write(quotation);
        }
        else {
            out.write(contents, startIndex, endIndex - startIndex);
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private int partialState = stateStartOfField;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parseFull(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount
    ) {
        recordRowBuffer.growTo(recordLength, fieldCount);
        partialState = stateStartOfField;

        int endIndex = recordOffset + recordLength;
        int fieldOffset = recordOffset;

        for (int i = 0; i < fieldCount; i++) {
            int length = parseField(recordRowBuffer, contentChars, fieldOffset, endIndex);

            fieldOffset += length;
            if (i != fieldCount - 1) {
                // NB: comma
                fieldOffset++;
            }
        }
    }


    private int parseField(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int fieldOffset,
            int endIndex
    ) {
        if (fieldOffset == endIndex) {
            recordRowBuffer.commitFieldUnsafe();
            return 0;
        }

        char first = contentChars[fieldOffset];
        if (first == ',') {
            recordRowBuffer.commitFieldUnsafe();
            return 0;
        }
        else if (first == '"') {
            int segmentStart = fieldOffset + 1;
            for (int i = fieldOffset + 1; i < endIndex; i++) {
                char nextChar = contentChars[i];
                if (nextChar == '"') {
                    if (i == endIndex - 1) {
                        recordRowBuffer.addToFieldAndCommitUnsafe(
                                contentChars, segmentStart, i - segmentStart - 1);
                        return i - fieldOffset;
                    }
                    else if (contentChars[i + 1] == '"') {
                        recordRowBuffer.addToFieldUnsafe(contentChars, segmentStart, i - segmentStart);
                        i++; // skip quoted quote
                        segmentStart = i;
                    }
                    else {
                        recordRowBuffer.addToFieldAndCommitUnsafe(
                                contentChars, segmentStart, i - segmentStart);
                        return i - fieldOffset + 1;
                    }
                }
            }
            throw new IllegalStateException();
        }
        else {
            for (int i = fieldOffset; i < endIndex; i++) {
                char nextChar = contentChars[i];
                if (nextChar == ',') {
                    int length = i - fieldOffset;
                    recordRowBuffer.addToFieldAndCommitUnsafe(contentChars, fieldOffset, length);
                    return length;
                }
            }
            recordRowBuffer.addToFieldAndCommitUnsafe(contentChars, fieldOffset, endIndex - fieldOffset);
            return endIndex - fieldOffset;
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parsePartial(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount,
            boolean endPartial
    ) {
        recordRowBuffer.growBy(recordLength, fieldCount);

        int end = recordOffset + recordLength;
        for (int i = recordOffset; i < end; i++) {
            partialState = nextPartial(contentChars[i], recordRowBuffer);
        }

        if (! endPartial) {
            recordRowBuffer.commitFieldUnsafe();
        }

        if (fieldCount > 1) {
            recordRowBuffer.indicateNonEmpty();
        }
    }


    private int nextPartial(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        return switch (partialState) {
            case stateStartOfField ->
                    onStartOfField(nextChar, recordLineBuffer);

            case stateEndOfRecord ->
                    onEndOfRecord(nextChar, recordLineBuffer);

            case stateInUnquoted ->
                    onUnquoted(nextChar, recordLineBuffer);

            case stateInQuoted ->
                    onQuoted(nextChar, recordLineBuffer);

            case stateInQuotedQuote ->
                    onQuotedQuote(nextChar, recordLineBuffer);

            default ->
                    throw new IllegalStateException("Unknown state: " + partialState);
        };
    }


    private int onStartOfField(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        switch (nextChar) {
            case quotation:
                return stateInQuoted;

            case delimiter:
                recordLineBuffer.commitFieldUnsafe();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                recordLineBuffer.commitFieldUnsafe();
                return stateEndOfRecord;

            default:
                recordLineBuffer.addToFieldUnsafe(nextChar);
                return stateInUnquoted;
        }
    }


    private int onEndOfRecord(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        switch (nextChar) {
            case quotation:
                return stateInQuoted;

            case delimiter:
                recordLineBuffer.commitFieldUnsafe();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                return stateEndOfRecord;

            default:
                recordLineBuffer.addToFieldUnsafe(nextChar);
                return stateInUnquoted;
        }
    }


    private int onUnquoted(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        switch (nextChar) {
            case delimiter -> {
                recordLineBuffer.commitFieldUnsafe();
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                recordLineBuffer.commitFieldUnsafe();
                return stateEndOfRecord;
            }

            case quotation ->
                    throw new IllegalStateException("Unexpected: '" + nextChar + "' - " + recordLineBuffer.toCsv());

            default -> {
                recordLineBuffer.addToFieldUnsafe(nextChar);
                return stateInUnquoted;
            }
        }
    }


    private int onQuoted(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        if (nextChar == quotation) {
            recordLineBuffer.indicateNonEmpty();
            return stateInQuotedQuote;
        }

        recordLineBuffer.addToFieldUnsafe(nextChar);
        return stateInQuoted;
    }


    private int onQuotedQuote(char nextChar, @NotNull RecordRowBuffer recordLineBuffer) {
        switch (nextChar) {
            case quotation -> {
                recordLineBuffer.addToFieldUnsafe(nextChar);
                return stateInQuoted;
            }

            case delimiter -> {
                recordLineBuffer.commitFieldUnsafe();
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                recordLineBuffer.commitFieldUnsafe();
                return stateEndOfRecord;
            }

            default ->
                    throw new IllegalStateException("unexpected: '" + nextChar + "'");
        }
    }
}
