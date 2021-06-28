package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.PipelineTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord;


public class FlatPipelineHandoff
        implements PipelineTerminalStep<FlatProcessorEvent, ModelOutputEvent<FlatFileRecord>>
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
            @NotNull PipelineOutput<ModelOutputEvent<FlatFileRecord>> output
    ) {
        if (model.getEndOfData()) {
            return;
        }

        ModelOutputEvent<FlatFileRecord> nextEvent = output.next();

        if (skipFirst) {
            nextEvent.setSkip(true);
            skipFirst = false;
        }
        else {
            nextEvent.setSkip(false);
        }

        FlatFileRecord flatFileRecord = nextEvent.modelOrInit(FlatFileRecord::new);
        flatFileRecord.exchange(model.model);

        FlatFileRecord row = (FlatFileRecord) nextEvent.getRow();
        row.clone(flatFileRecord);

        output.commit();
    }
}
