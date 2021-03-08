package tech.kzen.auto.server.objects.report.pipeline.input.parse.text.pipeline;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TextPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        RecordRowBuffer recordRowBuffer = model.model;

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = recordRowBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordRowBuffer.fieldEndsUnsafe();

        System.arraycopy(contentChars, 0, fieldContents, 0, charsLength);

        fieldEnds[0] = charsLength;
        recordRowBuffer.setCountAndLengthUnsafe(1, charsLength);
    }
}
