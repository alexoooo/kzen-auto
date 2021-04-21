package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import tech.kzen.auto.plugin.model.DataInputEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;


public class FlatProcessorEvent
        extends DataInputEvent
{
    public final FlatDataRecord model = new FlatDataRecord();
}
