package tech.kzen.auto.server.objects.report.input.parse;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;


public class FastCsvLineParser implements RecordLineParser {
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
    public static RecordLineBuffer parseLine(String line) {
        var parser = new FastCsvLineParser();
        var buffer = new RecordLineBuffer();
        char[] chars = line.toCharArray();
        parser.parseNext(buffer, chars, 0, chars.length);
        parser.endOfStream(buffer);
        return buffer;
    }


    public static List<RecordLineBuffer> parseLines(String lines) {
        var lineBuffers = new ArrayList<RecordLineBuffer>();
        var chars = lines.toCharArray();
        var parser = new FastCsvLineParser();

        var buffer = new RecordLineBuffer();

        var startIndex = 0;
        while (true) {
            var length = parser.parseNext(buffer, chars, startIndex, chars.length);
            if (length == -1) {
                break;
            }

            lineBuffers.add(buffer);
            buffer = new RecordLineBuffer();
            startIndex += length;
        }

        parser.endOfStream(buffer);
        if (! buffer.isEmpty()) {
            lineBuffers.add(buffer);
        }

        return lineBuffers;
    }


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
            @NotNull RecordLineBuffer recordLineBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        var recordLength = 0;
        for (int i = contentOffset; i < contentEnd; i++) {
            var nextChar = contentChars[i];
            recordLength++;

            var isEnd = parse(recordLineBuffer, nextChar);
            if (isEnd) {
                return recordLength;
            }
        }
        return -1;
    }


    @Override
    public boolean endOfStream(@NotNull RecordLineBuffer recordLineBuffer) {
        return parse(recordLineBuffer, (char) lineFeed);
    }


    @Override
    public boolean parse(@NotNull RecordLineBuffer recordLineBuffer, char nextChar) {
        int nextState = handleState(nextChar, recordLineBuffer);

        var previousState = state;
        state = nextState;

        return state == stateEndOfRecord &&
                previousState != stateEndOfRecord;
    }


    private int handleState(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
        switch (state) {
            case stateStartOfField:
                return onStartOfField(nextChar, recordLineBuffer);

            case stateEndOfRecord:
                return onEndOfRecord(nextChar, recordLineBuffer);

            case stateInUnquoted:
                return onUnquoted(nextChar, recordLineBuffer);

            case stateInQuoted:
                return onQuoted(nextChar, recordLineBuffer);

            case stateInQuotedQuote:
                return onQuotedQuote(nextChar, recordLineBuffer);

            default:
                throw new IllegalStateException("Unknown state: " + state);
        }
    }

    private int onStartOfField(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
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


    private int onEndOfRecord(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
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


    private int onUnquoted(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case delimiter:
                recordLineBuffer.commitField();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                recordLineBuffer.commitField();
                return stateEndOfRecord;

            case quotation:
                throw new IllegalStateException("Unexpected: '" + nextChar + "' - " + recordLineBuffer.toCsv());

            default:
                recordLineBuffer.addToField(nextChar);
                return stateInUnquoted;
        }
    }


    private int onQuoted(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
        if (nextChar == quotation) {
            return stateInQuotedQuote;
        }

        recordLineBuffer.addToField(nextChar);
        return stateInQuoted;
    }


    private int onQuotedQuote(char nextChar, @NotNull RecordLineBuffer recordLineBuffer) {
        switch ((int) nextChar) {
            case quotation:
                recordLineBuffer.addToField(nextChar);
                return stateInQuoted;

            case delimiter:
                recordLineBuffer.commitField();
                return stateStartOfField;

            case carriageReturn, lineFeed:
                recordLineBuffer.commitField();
                return stateEndOfRecord;

            default:
                throw new IllegalStateException("unexpected: '" + nextChar + "'");
        }
    }
}
