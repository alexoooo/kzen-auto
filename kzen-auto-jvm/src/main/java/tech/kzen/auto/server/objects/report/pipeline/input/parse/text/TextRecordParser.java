package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordParser;

import java.util.List;


public class TextRecordParser implements RecordParser
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final RecordHeader header = RecordHeader.Companion.of(List.of("Text"));


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void parseFull(
            @NotNull RecordItemBuffer recordItemBuffer,
            @NotNull char[] contentChars,
            int recordOffset,
            int recordLength,
            int fieldCount
    ) {
        recordItemBuffer.growTo(recordLength, 1);

        char[] fieldContents = recordItemBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordItemBuffer.fieldEndsUnsafe();

        System.arraycopy(contentChars, recordOffset, fieldContents, 0, recordLength);

        fieldEnds[0] = recordLength;
        recordItemBuffer.setCountAndLengthUnsafe(1, recordLength);
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
        int newField = endPartial ? 0 : 1;
        recordItemBuffer.growBy(recordLength, newField);

        int endIndex = recordOffset + recordLength;

        if (endPartial) {
            recordItemBuffer.addToFieldUnsafe(contentChars, endIndex - recordLength, recordLength);
        }
        else {
            recordItemBuffer.addToFieldAndCommitUnsafe(contentChars, endIndex - recordLength, recordLength);
        }

        recordItemBuffer.indicateNonEmpty();
    }
}