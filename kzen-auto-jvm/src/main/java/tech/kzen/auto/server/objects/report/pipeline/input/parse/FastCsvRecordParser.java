package tech.kzen.auto.server.objects.report.pipeline.input.parse;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;

import java.io.IOException;
import java.io.Writer;


public class FastCsvRecordParser implements RecordItemParser {
    //-----------------------------------------------------------------------------------------------------------------
    private static final int stateStartOfField = 0;
    private static final int stateInQuoted = 1;
    private static final int stateInQuotedQuote = 2;
    private static final int stateInUnquoted = 3;
    private static final int stateEndOfRecord = 4;

    public static final int quotation = '"';
    public static final int delimiter = ',';
    public static final int carriageReturn = '\r';
    public static final int lineFeed = '\n';


    //-----------------------------------------------------------------------------------------------------------------
//    public static RecordLineBuffer parseLine(String line) {
//        var parser = new FastCsvLineParser();
//        var buffer = new RecordLineBuffer();
//        char[] chars = line.toCharArray();
//        parser.parseNext(buffer, chars, 0, chars.length);
//        parser.endOfStream(buffer);
//        return buffer;
//    }


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
    private int state = stateStartOfField;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int parseNext(
            @NotNull RecordItemBuffer recordItemBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        int recordLength = 0;
        for (int i = contentOffset; i < contentEnd; i++) {
            char nextChar = contentChars[i];
            recordLength++;

            if (state == stateInQuoted) {
                int length = parseInQuoteUntilNextState(
                        recordItemBuffer, contentChars, i, contentEnd, nextChar);
                if (length == -1) {
                    return -1;
                }
                i += length;
                nextChar = contentChars[i];
                recordLength += length;
            }
            else if (state == stateInUnquoted) {
                int length = parseInUnquotedUntilNextState(
                        recordItemBuffer, contentChars, i, contentEnd, nextChar);
                if (length == -1) {
                    return -1;
                }
                i += length;
                nextChar = contentChars[i];
                recordLength += length;
            }

            var isEnd = parse(recordItemBuffer, nextChar);
            if (isEnd) {
                return recordLength;
            }
        }
        return -1;
    }


    private int parseInQuoteUntilNextState(
            RecordItemBuffer recordLineBuffer,
            char[] contentChars,
            int start,
            int contentEnd,
            char startChar
    ) {
        int length = 0;
        boolean reachedNextState = false;
        int i = start;
        char nextChar = startChar;

        while (true) {
            if (nextChar == quotation) {
                reachedNextState = true;
                break;
            }
            i++;
            if (i == contentEnd) {
                break;
            }
            nextChar = contentChars[i];
            length++;
        }

        if (! reachedNextState) {
            length++;
        }

        recordLineBuffer.addToField(contentChars, start, i - start);
        return reachedNextState ? length : -1;
    }


    private int parseInUnquotedUntilNextState(
            RecordItemBuffer recordLineBuffer,
            char[] contentChars,
            int start,
            int contentEnd,
            char startChar
    ) {
        int length = 0;
        boolean reachedNextState = false;
        int i = start;
        int nextChar = startChar;

        scan:
        while (true) {
            switch (nextChar) {
                case quotation, delimiter, carriageReturn, lineFeed -> {
                    reachedNextState = true;
                    break scan;
                }

                default -> {
                    i++;
                    if (i == contentEnd) {
                        break scan;
                    }
                    nextChar = contentChars[i];
                    length++;
                }
            }
        }

        if (! reachedNextState) {
            length++;
        }

        recordLineBuffer.addToField(contentChars, start, length);
        return reachedNextState ? length : -1;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void endOfStream(@NotNull RecordItemBuffer recordItemBuffer) {
        parse(recordItemBuffer, (char) lineFeed);
    }


    private boolean parse(@NotNull RecordItemBuffer recordLineBuffer, char nextChar) {
        int nextState = handleState(nextChar, recordLineBuffer);

        var previousState = state;
        state = nextState;

        return state == stateEndOfRecord &&
                previousState != stateEndOfRecord;
    }


    private int handleState(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        return switch (state) {
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
                    throw new IllegalStateException("Unknown state: " + state);
        };
    }


    private int onStartOfField(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case quotation:
                return stateInQuoted;

            case delimiter:
                recordLineBuffer.commitField();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                recordLineBuffer.commitField();
                return stateEndOfRecord;

            default:
                recordLineBuffer.addToField(nextChar);
                return stateInUnquoted;
        }
    }


    private int onEndOfRecord(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case quotation:
                return stateInQuoted;

            case delimiter:
                recordLineBuffer.commitField();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                return stateEndOfRecord;

            default:
                recordLineBuffer.addToField(nextChar);
                return stateInUnquoted;
        }
    }


    private int onUnquoted(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case delimiter -> {
                recordLineBuffer.commitField();
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                recordLineBuffer.commitField();
                return stateEndOfRecord;
            }

            case quotation ->
                    throw new IllegalStateException("Unexpected: '" + nextChar + "' - " + recordLineBuffer.toCsv());

            default -> {
                recordLineBuffer.addToField(nextChar);
                return stateInUnquoted;
            }
        }
    }


    private int onQuoted(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        if (nextChar == quotation) {
            recordLineBuffer.indicateNonEmpty();
            return stateInQuotedQuote;
        }

        recordLineBuffer.addToField(nextChar);
        return stateInQuoted;
    }


    private int onQuotedQuote(char nextChar, @NotNull RecordItemBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case quotation -> {
                recordLineBuffer.addToField(nextChar);
                return stateInQuoted;
            }

            case delimiter -> {
                recordLineBuffer.commitField();
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                recordLineBuffer.commitField();
                return stateEndOfRecord;
            }

            default ->
                    throw new IllegalStateException("unexpected: '" + nextChar + "'");
        }
    }
}
