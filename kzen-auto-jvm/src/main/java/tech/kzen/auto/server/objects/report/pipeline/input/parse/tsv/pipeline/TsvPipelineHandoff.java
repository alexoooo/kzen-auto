package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.PipelineTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TsvPipelineHandoff
    implements PipelineTerminalStep<FlatProcessorEvent, RecordItemBuffer>
{
    @Override
    public void process(
            FlatProcessorEvent model,
            @NotNull PipelineOutput<RecordItemBuffer> output
    ) {
        RecordItemBuffer recordItemBuffer = output.next();
        recordItemBuffer.copy(model.model);
        output.commit();
    }
}
