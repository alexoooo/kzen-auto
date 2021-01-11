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

        int fieldCount = (partial ? 0 : 1);
        int startOffset = 0;

        for (int i = contentOffset; i < contentEnd; i++) {
            char nextChar = contentChars[i];

            if (nextChar > 13) { // NB: max of (delimiter, lineFeed, carriageReturn)
                if (fieldCount == 0) {
                    fieldCount = 1;
                }
                partial = true;
            }
            else if (nextChar == '\t') {
                fieldCount++;
                partial = true;
            }
            else if (nextChar == '\r' || nextChar == '\n') {
                if (fieldCount > 0 || partial) {
                    recordTokenBuffer.add(startOffset, i - startOffset, fieldCount);
                    fieldCount = 0;
                }
                startOffset = i + 1;
                partial = false;
            }
            else {
                // NB: unusual field content
                if (fieldCount == 0) {
                    fieldCount = 1;
                }
                partial = true;
            }
        }

        if (partial) {
            recordTokenBuffer.add(startOffset, contentEnd - startOffset, fieldCount);
            recordTokenBuffer.setPartialLast();
        }
    }


    @Override
    public void endOfStream(@NotNull RecordTokenBuffer recordTokenBuffer) {
        recordTokenBuffer.clearPartialLast();
    }
}
