package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTokenBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordLexer;


public class TextRecordLexer implements RecordLexer
{
    //-----------------------------------------------------------------------------------------------------------------
    private boolean partial = true;


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
                ? (firstChar == '\r' || firstChar == '\n' ? 0 : 1)
                : 1;

        int startOffset = 0;

        for (int i = contentOffset; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            if (nextChar > 13) { // NB: max of (lineFeed, carriageReturn)
                nextPartial = true;
            }
            else if (nextChar == '\r') {
                recordTokenBuffer.add(startOffset, i - startOffset, fieldCount);
                fieldCount = 1;
                startOffset = i + 1;

                if (i + 1 < contentEnd && contentChars[i + 1] == '\n') {
                    i++;
                    startOffset++;
                    nextPartial = true;
                }
                else {
                    nextPartial = false;
                }
            }
            else if (nextChar == '\n') {
                if (nextPartial) {
                    recordTokenBuffer.add(startOffset, i - startOffset, fieldCount);
                }
                fieldCount = 1;
                startOffset = i + 1;
                nextPartial = true;
            }
            else {
                nextPartial = true;
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

        partial = true;
    }
}