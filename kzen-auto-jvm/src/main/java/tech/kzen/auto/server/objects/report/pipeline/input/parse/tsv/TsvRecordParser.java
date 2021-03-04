package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser;


public class TsvRecordParser implements RecordParser
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final int delimiterInt = '\t';


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parseFull(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount
    ) {
        recordRowBuffer.growTo(recordLength, fieldCount);

        char[] fieldContents = recordRowBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordRowBuffer.fieldEndsUnsafe();

        int nextFieldCount = recordRowBuffer.fieldCount();
        int nextFieldContentLength = recordRowBuffer.fieldContentLength();

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
        recordRowBuffer.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parsePartial(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount,
            boolean endPartial
    ) {
        recordRowBuffer.growBy(recordLength, fieldCount);

        int fieldLength = 0;
        int endIndex = recordOffset + recordLength;
        for (int i = recordOffset; i < endIndex; i++) {
            char nextChar = contentChars[i];

            if (nextChar == '\t') {
                recordRowBuffer.addToFieldAndCommitUnsafe(contentChars, i - fieldLength, fieldLength);
                fieldLength = 0;
            }
            else {
                fieldLength++;
            }
        }

        if (endPartial) {
            recordRowBuffer.addToFieldUnsafe(contentChars, endIndex - fieldLength, fieldLength);
        }
        else {
            recordRowBuffer.addToFieldAndCommitUnsafe(contentChars, endIndex - fieldLength, fieldLength);
        }

        if (fieldCount > 1) {
            recordRowBuffer.indicateNonEmpty();
        }
    }
}
