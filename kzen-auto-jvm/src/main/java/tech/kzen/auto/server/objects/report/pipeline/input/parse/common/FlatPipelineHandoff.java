package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.PipelineTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;


public class FlatPipelineHandoff
        implements PipelineTerminalStep<FlatProcessorEvent, ModelOutputEvent<FlatDataRecord>>
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
            @NotNull PipelineOutput<ModelOutputEvent<FlatDataRecord>> output
    ) {
        ModelOutputEvent<FlatDataRecord> nextEvent = output.next();

        if (skipFirst) {
            nextEvent.setSkip(true);
            skipFirst = false;
        }
        else {
            nextEvent.setSkip(false);
        }

        FlatDataRecord flatDataRecord = nextEvent.modelOrInit(FlatDataRecord::new);
        flatDataRecord.copy(model.model);

        FlatDataRecord row = (FlatDataRecord) nextEvent.getRow();
        row.clone(flatDataRecord);

        output.commit();
    }
}
