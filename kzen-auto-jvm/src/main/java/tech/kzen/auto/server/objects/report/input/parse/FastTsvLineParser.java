package tech.kzen.auto.server.objects.report.input.parse;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer;


public class FastTsvLineParser implements RecordLineParser
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final char delimiter = '\t';
    private static final char carriageReturn = '\r';
    private static final char lineFeed = '\n';

    public static final int delimiterInt = delimiter;


    //-----------------------------------------------------------------------------------------------------------------
    private boolean stateAtEnd = false;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int parseNext(
            @NotNull RecordLineBuffer recordLineBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        int recordLength = 0;
        int i = contentOffset;
        while (i < contentEnd) {
            char nextChar = contentChars[i];
            recordLength++;

            if (! stateAtEnd) {
                int length = parseInFieldUntilNext(
                        recordLineBuffer, contentChars, i, contentEnd, nextChar);
                if (length == -1) {
                    return -1;
                }
                i += length;
                nextChar = contentChars[i];
                recordLength += length;
            }

            boolean isEnd = parse(recordLineBuffer, nextChar);
            if (isEnd) {
                return recordLength;
            }

            i++;
        }
        return -1;
    }


    @Override
    public boolean endOfStream(
            @NotNull RecordLineBuffer recordLineBuffer
    ) {
        return parse(recordLineBuffer, lineFeed);
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return length until next state, or -1 if reached contentEnd without seeing end of field
     */
    private int parseInFieldUntilNext(
            RecordLineBuffer recordLineBuffer,
            char[] contentChars,
            int start,
            int contentEnd,
            char startChar
    ) {
        boolean reachedNext = false;
        int i = start;
        int nextChar = startChar;

        while (true) {
            if (nextChar == delimiter ||
                    nextChar == carriageReturn ||
                    nextChar == lineFeed
            ) {
                reachedNext = true;
                break;
            }

            i++;

            if (i == contentEnd) {
                break;
            }
            nextChar = contentChars[i];
        }

        int length = i - start;
        recordLineBuffer.addToField(contentChars, start, length);

        return reachedNext ? length : -1;
    }


    /**
     * @return true if reached end of record
     */
    private boolean parse(
            RecordLineBuffer recordLineBuffer,
            char nextChar
    ) {
        boolean nextStateAtEnd;

        if (nextChar == carriageReturn ||
                nextChar == lineFeed)
        {
            if (! stateAtEnd) {
                recordLineBuffer.commitField();
            }
            nextStateAtEnd = true;
        }
        else {
            if (nextChar == delimiter) {
                recordLineBuffer.commitField();
            }
            else {
                recordLineBuffer.addToField(nextChar);
            }
            nextStateAtEnd = false;
        }

        boolean previousStateAtEnd = stateAtEnd;
        stateAtEnd = nextStateAtEnd;

        return stateAtEnd &&
                ! previousStateAtEnd;
    }
}
