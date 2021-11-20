package tech.kzen.auto.server.objects.report.exec.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.ReportTerminalStep;
import tech.kzen.auto.plugin.api.managed.PipelineOutput;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;

import java.util.concurrent.CountDownLatch;


public class FlatPipelineHandoff
        implements ReportTerminalStep<FlatReportEvent, ModelOutputEvent<FlatFileRecord>>
{
    //-----------------------------------------------------------------------------------------------------------------
    private final CountDownLatch endOfData = new CountDownLatch(1);
    private boolean skipFirst;


    //-----------------------------------------------------------------------------------------------------------------
    public FlatPipelineHandoff(boolean skipFirst) {
        this.skipFirst = skipFirst;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(
            FlatReportEvent model,
            @NotNull PipelineOutput<ModelOutputEvent<FlatFileRecord>> output
    ) {
        if (model.getEndOfData()) {
            endOfData.countDown();
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

        FlatFileRecord row = nextEvent.getRow();
        row.clone(flatFileRecord);

        output.commit();
    }


    @Override
    public void awaitEndOfData() {
        try {
            endOfData.await();
        }
        catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
