package tech.kzen.auto.server.objects.report.exec.input.parse.common;


import tech.kzen.auto.plugin.model.DataInputEvent;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;


public class FlatReportEvent
        extends DataInputEvent
{
    public final FlatFileRecord model = new FlatFileRecord();
}
