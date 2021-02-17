package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTokenBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexer;


public class CsvRecordLexer implements RecordLexer
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


    //-----------------------------------------------------------------------------------------------------------------
    private boolean partial = false;
    private int state = stateStartOfField;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void tokenize(
            @NotNull RecordTokenBuffer recordTokenBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        recordTokenBuffer.clear();

        int contentStart = contentOffset;
        if (state == stateEndOfRecord) {
            while (contentStart < contentEnd) {
                char nextChar = contentChars[contentStart];
                if (! (nextChar == '\r' || nextChar == '\n')) {
                    break;
                }
                contentStart++;
            }
        }

        char firstChar = contentChars[contentStart];
        int fieldCount = (partial ? 0 : (firstChar == '\r' || firstChar == '\n' ? 0 : 1));
        int startOffset = contentStart - contentOffset;

        for (int i = contentStart; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            int nextState = nextState(nextChar);

            if (nextState == stateStartOfField) {
                if (fieldCount == 0 && contentChars[i] == ',') {
                    fieldCount++;
                }
                fieldCount++;
                partial = true;
            }
            else if (nextState == stateEndOfRecord) {
                if (fieldCount > 0 || partial) {
                    recordTokenBuffer.add(startOffset, i - startOffset, fieldCount);
                    fieldCount = 0;
                }
                startOffset = i + 1;
                partial = false;
            }
            else {
                if (fieldCount == 0) {
                    fieldCount = 1;
                }
                partial = true;
            }

            state = nextState;
        }

        if (partial) {
            recordTokenBuffer.add(startOffset, contentEnd - startOffset, fieldCount);
            recordTokenBuffer.setPartialLast();
        }
    }


    @Override
    public void endOfStream(@NotNull RecordTokenBuffer recordTokenBuffer) {
        recordTokenBuffer.clearPartialLast();

        partial = false;
        state = stateStartOfField;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private int nextState(char nextChar) {
        return switch (state) {
            case stateStartOfField, stateEndOfRecord ->
                    onBetweenFields(nextChar);

            case stateInUnquoted ->
                    onUnquoted(nextChar);

            case stateInQuoted ->
                    onQuoted(nextChar);

            case stateInQuotedQuote ->
                    onQuotedQuote(nextChar);

            default ->
                    throw new IllegalStateException("Unknown state: " + state);
        };
    }


    private int onBetweenFields(char nextChar) {
        return switch (nextChar) {
            case quotation ->
                    stateInQuoted;

            case delimiter ->
                    stateStartOfField;

            case carriageReturn, lineFeed ->
                    stateEndOfRecord;

            default ->
                    stateInUnquoted;
        };
    }


    private int onUnquoted(char nextChar) {
        switch (nextChar) {
            case delimiter -> {
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                return stateEndOfRecord;
            }

            case quotation ->
                    throw new IllegalStateException("Unexpected: '" + nextChar + "'");

            default -> {
                return stateInUnquoted;
            }
        }
    }


    private int onQuoted(char nextChar) {
        return nextChar == quotation
                ? stateInQuotedQuote
                : stateInQuoted;
    }


    private int onQuotedQuote(char nextChar) {
        switch (nextChar) {
            case quotation -> {
                return stateInQuoted;
            }

            case delimiter -> {
                return stateStartOfField;
            }

            case carriageReturn, lineFeed -> {
                return stateEndOfRecord;
            }

            default ->
                    throw new IllegalStateException("unexpected: '" + nextChar + "'");
        }
    }
}
