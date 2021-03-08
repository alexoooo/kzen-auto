package tech.kzen.auto.server.objects.report.pipeline.input.parse.text.pipeline;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TextPipelineLexer
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        int charsLength = data.charsLength;

        model.model.growTo(charsLength, 1);
    }
}
