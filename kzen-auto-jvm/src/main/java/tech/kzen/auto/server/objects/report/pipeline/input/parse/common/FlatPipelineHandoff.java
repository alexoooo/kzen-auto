package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.PipelineTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;


public class FlatPipelineHandoff
        implements PipelineTerminalStep<FlatProcessorEvent, ModelOutputEvent<RecordRowBuffer>>
{
    //-----------------------------------------------------------------------------------------------------------------
    private boolean skipFirst;


    //-----------------------------------------------------------------------------------------------------------------
    public FlatPipelineHandoff(boolean skipFirst) {
        this.skipFirst = skipFirst;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(
            FlatProcessorEvent model,
            @NotNull PipelineOutput<ModelOutputEvent<RecordRowBuffer>> output
    ) {
        ModelOutputEvent<RecordRowBuffer> nextEvent = output.next();

        if (skipFirst) {
            nextEvent.setSkip(model.skip);
            skipFirst = false;
        }

        RecordRowBuffer recordRowBuffer = nextEvent.modelOrInit(RecordRowBuffer::new);
        recordRowBuffer.copy(model.model);

        output.commit();
    }
}
