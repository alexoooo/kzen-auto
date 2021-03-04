package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.PipelineTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TsvPipelineHandoff
    implements PipelineTerminalStep<FlatProcessorEvent, ModelOutputEvent<RecordRowBuffer>>
{
    @Override
    public void process(
            FlatProcessorEvent model,
            @NotNull PipelineOutput<ModelOutputEvent<RecordRowBuffer>> output
    ) {
        if (model.skip) {
            return;
        }

        ModelOutputEvent<RecordRowBuffer> nextEvent = output.next();
        RecordRowBuffer recordRowBuffer = nextEvent.modelOrInit(RecordRowBuffer::new);
        recordRowBuffer.copy(model.model);
        output.commit();
    }
}
