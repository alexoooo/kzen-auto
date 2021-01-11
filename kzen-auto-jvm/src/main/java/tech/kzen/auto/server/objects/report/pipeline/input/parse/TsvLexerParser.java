package tech.kzen.auto.server.objects.report.pipeline.input.parse;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;


public class TsvLexerParser implements RecordLexerParser
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parseFull(
            @NotNull RecordItemBuffer recordItemBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount
    ) {
        recordItemBuffer.growTo(recordLength, fieldCount);

        char[] fieldContents = recordItemBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordItemBuffer.fieldEndsUnsafe();

        int nextFieldCount = recordItemBuffer.fieldCount();
        int nextFieldContentLength = recordItemBuffer.fieldContentLength();

        int endIndex = recordOffset + recordLength;
        for (int i = recordOffset; i < endIndex; i++) {
            char nextChar = contentChars[i];

            if (nextChar == '\t') {
                fieldEnds[nextFieldCount++] = nextFieldContentLength;
            }
            else {
                fieldContents[nextFieldContentLength++] = nextChar;
            }
        }

        fieldEnds[nextFieldCount++] = nextFieldContentLength;
        recordItemBuffer.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parsePartial(
            @NotNull RecordItemBuffer recordItemBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount,
            boolean endPartial
    ) {
        recordItemBuffer.growBy(recordLength, fieldCount);

        int fieldLength = 0;
        int endIndex = recordOffset + recordLength;
        for (int i = recordOffset; i < endIndex; i++) {
            char nextChar = contentChars[i];

            if (nextChar == '\t') {
                recordItemBuffer.addToFieldAndCommitUnsafe(contentChars, i - fieldLength, fieldLength);
                fieldLength = 0;
            }
            else {
                fieldLength++;
            }
        }

        if (endPartial) {
            recordItemBuffer.addToFieldUnsafe(contentChars, endIndex - fieldLength, fieldLength);
        }
        else {
            recordItemBuffer.addToFieldAndCommitUnsafe(contentChars, endIndex - fieldLength, fieldLength);
        }
    }
}
