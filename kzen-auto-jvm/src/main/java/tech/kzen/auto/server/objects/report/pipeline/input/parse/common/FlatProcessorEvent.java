package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.model.DataInputEvent;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;


public class FlatProcessorEvent
        extends DataInputEvent
        implements ModelOutputEvent<RecordItemBuffer>
{
    public final RecordItemBuffer model = new RecordItemBuffer();
    public boolean skip = false;


    @NotNull
    @Override
    public Class<RecordItemBuffer> getType() {
        return RecordItemBuffer.class;
    }


    @Override
    public RecordItemBuffer getModel() {
        return model;
    }


    @Override
    public boolean getSkip() {
        return skip;
    }
}
