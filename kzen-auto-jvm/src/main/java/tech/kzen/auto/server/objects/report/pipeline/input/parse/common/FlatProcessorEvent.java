package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import tech.kzen.auto.plugin.model.DataInputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;


public class FlatProcessorEvent
        extends DataInputEvent
{
    public final RecordRowBuffer model = new RecordRowBuffer();
    public boolean skip = false;


//    @NotNull
//    @Override
//    public Class<RecordRowBuffer> getType() {
//        return RecordRowBuffer.class;
//    }
//
//
//    @Override
//    public RecordRowBuffer getModel() {
//        return model;
//    }
//
//
//    @Override
//    public boolean getSkip() {
//        return skip;
//    }
}
