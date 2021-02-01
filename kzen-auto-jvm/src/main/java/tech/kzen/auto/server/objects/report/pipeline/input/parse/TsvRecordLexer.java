package tech.kzen.auto.server.objects.report.pipeline.input.parse;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTokenBuffer;


public class TsvRecordLexer implements RecordLexer
{
    //-----------------------------------------------------------------------------------------------------------------
    private boolean partial = false;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void tokenize(
            @NotNull RecordTokenBuffer recordTokenBuffer,
            @NotNull char[] contentChars,
            int contentOffset,
            int contentEnd
    ) {
        recordTokenBuffer.clear();

        boolean nextPartial = partial;

        char firstChar = contentChars[contentOffset];

        int fieldCount =
                nextPartial
                ? (firstChar == '\t' || firstChar == '\r' || firstChar == '\n' ? 0 : 1)
                : 1;

        int startOffset = 0;

        for (int i = contentOffset; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            if (nextChar > 13) { // NB: max of (delimiter, lineFeed, carriageReturn)
                nextPartial = true;
            }
            else {
                switch (nextChar) {
                    case 9 -> {
                        fieldCount++;
                        nextPartial = true;
                    }

                    case 10, 13 -> {
                        if (fieldCount > 0 || nextPartial) {
                            int length = i - startOffset;
                            if (length > 0 || fieldCount > 1 || nextPartial) {
                                recordTokenBuffer.add(startOffset, i - startOffset, fieldCount);
                            }
                            if (i + 1 < contentEnd) {
                                char nextNextChar = contentChars[i + 1];
                                fieldCount =
                                        nextNextChar == '\t' ||
                                        nextNextChar == '\r' ||
                                        nextNextChar == '\n'
                                        ? 0 : 1;
                            }
                            else {
                                fieldCount = 0;
                            }
                        }
                        startOffset = i + 1;
                        nextPartial = false;
                    }

                    default ->
                            nextPartial = true;
                }
            }
        }

        if (nextPartial) {
            recordTokenBuffer.add(startOffset, contentEnd - startOffset, fieldCount);
            recordTokenBuffer.setPartialLast();
        }
        partial = nextPartial;
    }


    @Override
    public void endOfStream(@NotNull RecordTokenBuffer recordTokenBuffer) {
        recordTokenBuffer.clearPartialLast();
    }
}