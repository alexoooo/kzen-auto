package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser;

import java.util.List;


public class TextRecordParser implements RecordParser
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final RecordHeader header = RecordHeader.Companion.of(List.of("Text"));


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parseFull(
            @NotNull RecordRowBuffer recordRowBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount
    ) {
        recordRowBuffer.growTo(recordLength, 1);

        char[] fieldContents = recordRowBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordRowBuffer.fieldEndsUnsafe();

        System.arraycopy(contentChars, recordOffset, fieldContents, 0, recordLength);

        fieldEnds[0] = recordLength;
        recordRowBuffer.setCountAndLengthUnsafe(1, recordLength);
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
        int newField = endPartial ? 0 : 1;
        recordRowBuffer.growBy(recordLength, newField);

        int endIndex = recordOffset + recordLength;

        if (endPartial) {
            recordRowBuffer.addToFieldUnsafe(contentChars, endIndex - recordLength, recordLength);
        }
        else {
            recordRowBuffer.addToFieldAndCommitUnsafe(contentChars, endIndex - recordLength, recordLength);
        }

        recordRowBuffer.indicateNonEmpty();
    }
}