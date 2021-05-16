package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TextPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        if (model.getEndOfData()) {
            return;
        }

        RecordDataBuffer data = model.getData();
        FlatFileRecord flatFileRecord = model.model;
        flatFileRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatFileRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatFileRecord.fieldEndsUnsafe();

        System.arraycopy(contentChars, 0, fieldContents, 0, charsLength);

        fieldEnds[0] = charsLength;
        flatFileRecord.setCountAndLengthUnsafe(1, charsLength);
    }
}
