package tech.kzen.auto.server.objects.report.pipeline.input.parse;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;


public class FastTsvRecordParser implements RecordItemParser
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final char delimiter = '\t';
    private static final char carriageReturn = '\r';
    private static final char lineFeed = '\n';

    public static final int delimiterInt = delimiter;


    //-----------------------------------------------------------------------------------------------------------------
    private boolean stateAtEnd = false;
//    private StringBuilder buff = new StringBuilder();
//    private int count = 0;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int parseNext(
            @NotNull RecordItemBuffer recordLineBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
//        count++;

        int recordLength = 0;
        int i = contentOffset;
        while (i < contentEnd) {
            char nextChar = contentChars[i];
//            buff.append(nextChar);

            recordLength++;

            if (! stateAtEnd) {
                int length = parseInFieldUntilNext(
                        recordLineBuffer, contentChars, i, contentEnd, nextChar);
                if (length == -1) {
                    return -1;
                }
                i += length;
                recordLength += length;
                nextChar = contentChars[i];
            }

            boolean isEnd = parse(recordLineBuffer, nextChar);
            if (isEnd) {
//                String str = buff.toString();
//                if (! recordLineBuffer.toTsv().equals(str.trim())) {
//                    System.out.println("foo: " + count);
//                }
//                buff.setLength(0);
                return recordLength;
            }

            i++;
        }
        return -1;
    }


    @Override
    public boolean endOfStream(
            @NotNull RecordItemBuffer recordLineBuffer
    ) {
        return parse(recordLineBuffer, lineFeed);
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return length until next state, or -1 if reached contentEnd without seeing end of field
     */
    private int parseInFieldUntilNext(
            RecordItemBuffer recordLineBuffer,
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
//            buff.append((char) nextChar);
        }

        int length = i - start;
        recordLineBuffer.addToField(contentChars, start, length);

        return reachedNext ? length : -1;
    }


    /**
     * @return true if reached end of record
     */
    private boolean parse(
            RecordItemBuffer recordLineBuffer,
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
