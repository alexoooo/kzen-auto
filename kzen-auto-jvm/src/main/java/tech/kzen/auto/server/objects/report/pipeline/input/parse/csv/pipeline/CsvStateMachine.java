package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.pipeline;


public enum CsvStateMachine
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
}
