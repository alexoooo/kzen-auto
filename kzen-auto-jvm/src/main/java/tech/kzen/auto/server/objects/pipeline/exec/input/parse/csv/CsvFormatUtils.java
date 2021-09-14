package tech.kzen.auto.server.objects.pipeline.exec.input.parse.csv;


import java.io.IOException;
import java.io.Writer;


public enum CsvFormatUtils
{;
    //-----------------------------------------------------------------------------------------------------------------
    public static final int stateStartOfField = 0;
    public static final int stateInQuoted = 1;
    public static final int stateInQuotedQuote = 2;
    public static final int stateInUnquoted = 3;
    public static final int stateEndOfRecord = 4;

    public static final int stateInitial = stateStartOfField;

    public static final char quotation = '"';
    public static final char delimiter = ',';
    public static final char carriageReturn = '\r';
    public static final char lineFeed = '\n';

    public static final int delimiterInt = delimiter;
    public static final int lineFeedInt = lineFeed;


    //-----------------------------------------------------------------------------------------------------------------
    public static int nextState(int currentState, char nextChar) {
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


    private static int onBetweenFields(char nextChar) {
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


    private static int onUnquoted(char nextChar) {
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


    private static int onQuoted(char nextChar) {
        return nextChar == quotation
                ? stateInQuotedQuote
                : stateInQuoted;
    }


    private static int onQuotedQuote(char nextChar) {
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


    //-----------------------------------------------------------------------------------------------------------------
    public static int nextFramedState(int currentState, char nextChar) {
        return switch (currentState) {
            case stateStartOfField ->
                    switch (nextChar) {
                        case quotation -> stateInQuoted;
                        case delimiter -> stateStartOfField;
                        default -> stateInUnquoted;
                    };

            case stateInUnquoted ->
                    nextChar == delimiter
                            ? stateStartOfField
                            : stateInUnquoted;

            case stateInQuoted ->
                    nextChar == quotation
                            ? stateInQuotedQuote
                            : stateInQuoted;

            case stateInQuotedQuote ->
                    nextChar == quotation
                            ? stateInQuoted
                            : stateStartOfField;

            default ->
                    throw new IllegalStateException("Unknown state: " + currentState);
        };
    }


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
}
