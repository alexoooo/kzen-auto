package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import tech.kzen.auto.plugin.model.DataInputEvent;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;


public class FlatProcessorEvent
        extends DataInputEvent
{
    public final FlatFileRecord model = new FlatFileRecord();
}
