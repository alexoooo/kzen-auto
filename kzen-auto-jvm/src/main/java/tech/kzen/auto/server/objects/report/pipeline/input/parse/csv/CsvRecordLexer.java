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
    private boolean midDelimiter = false;
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
        if (midDelimiter) {
            contentStart++;
            midDelimiter = false;
        }

        boolean nextPartial = partial;
        int nextState = state;

        char firstChar = contentChars[contentStart];
        int fieldCount = (nextPartial ? 0 : (firstChar == '\r' || firstChar == '\n' ? 0 : 1));
        int startOffset = contentStart - contentOffset;

        for (int i = contentStart; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            nextState = nextState(nextState, nextChar);

            if (nextState == stateStartOfField) {
                if (fieldCount == 0 && contentChars[i] == ',') {
                    fieldCount++;
                }
                fieldCount++;
                nextPartial = true;
            }
            else if (nextState == stateEndOfRecord) {
                recordTokenBuffer.add(startOffset, i - startOffset, Math.max(1, fieldCount));
                fieldCount = 0;

                if (nextChar == '\r') {
                    i++;
                    if (i == contentEnd) {
                        midDelimiter = true;
                    }
                }

                startOffset = i + 1;
                nextPartial = false;
            }
            else {
                if (fieldCount == 0) {
                    fieldCount = 1;
                }
                nextPartial = true;
            }
        }

        if (nextPartial) {
            recordTokenBuffer.add(startOffset, contentEnd - startOffset, fieldCount);
            recordTokenBuffer.setPartialLast();
        }

        state = nextState;
        partial = nextPartial;
    }


    @Override
    public void endOfStream(@NotNull RecordTokenBuffer recordTokenBuffer) {
        recordTokenBuffer.clearPartialLast();

        partial = false;
        midDelimiter = false;
        state = stateStartOfField;
    }


    //-----------------------------------------------------------------------------------------------------------------
    private int nextState(int currentState, char nextChar) {
        return switch (currentState) {
            case stateStartOfField, stateEndOfRecord ->
                    onBetweenFields(nextChar);

            case stateInUnquoted ->
                    onUnquoted(nextChar);

            case stateInQuoted ->
                    onQuoted(nextChar);

            case stateInQuotedQuote ->
                    onQuotedQuote(nextChar);

            default ->
                    throw new IllegalStateException("Unknown state: " + currentState);
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
