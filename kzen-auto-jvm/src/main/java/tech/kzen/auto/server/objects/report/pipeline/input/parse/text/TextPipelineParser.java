package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TextPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        FlatDataRecord flatDataRecord = model.model;
        flatDataRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatDataRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatDataRecord.fieldEndsUnsafe();

        System.arraycopy(contentChars, 0, fieldContents, 0, charsLength);

        fieldEnds[0] = charsLength;
        flatDataRecord.setCountAndLengthUnsafe(1, charsLength);
    }
}
