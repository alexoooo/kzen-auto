package tech.kzen.auto.server.objects.report.pipeline.input.parse;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;


public class TsvRecordParser implements RecordParser
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final int delimiterInt = '\t';


    //-----------------------------------------------------------------------------------------------------------------
    private boolean stateAtEnd = false;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int parseNext(
            @NotNull RecordItemBuffer recordItemBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        stateAtEnd = false;

        int fieldChars = 0;
        for (int i = contentOffset; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            if (nextChar > 13) { // NB: max of (delimiter, lineFeed, carriageReturn)
                fieldChars++;
            }
            else if (nextChar == 9) { // delimiter
                recordItemBuffer.addToFieldAndCommit(contentChars, i - fieldChars, fieldChars);
                fieldChars = 0;
            }
            else {
                switch (nextChar) {
                    // lineFeed
                    case 10 -> {
                        if (fieldChars > 0 || ! recordItemBuffer.isEmpty()) {
                            recordItemBuffer.addToFieldAndCommit(contentChars, i - fieldChars, fieldChars);
                        }
                        stateAtEnd = true;
                        return i - contentOffset + 1;
                    }

                    // carriageReturn
                    case 13 -> {
                        if (fieldChars > 0 || ! recordItemBuffer.isEmpty()) {
                            recordItemBuffer.addToFieldAndCommit(contentChars, i - fieldChars, fieldChars);
                        }
                        stateAtEnd = true;
                        int endChars =
                                i + 1 < contentEnd &&
                                contentChars[i + 1] == 10 /* lineFeed */ ? 2 : 1;
                        return i - contentOffset + endChars;
                    }

                    default ->
                        fieldChars++;
                }
            }
        }

        if (fieldChars > 0) {
            recordItemBuffer.addToField(contentChars, contentEnd - fieldChars, fieldChars);
        }

        return -1;
    }


    @Override
    public void endOfStream(
            @NotNull RecordItemBuffer recordItemBuffer
    ) {
        if (! stateAtEnd) {
            recordItemBuffer.commitField();
        }
    }
}
