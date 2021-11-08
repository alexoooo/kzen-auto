package tech.kzen.auto.server.objects.report.exec.input.parse.text;


import tech.kzen.auto.plugin.api.ReportInputIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatReportEvent;


public class TextPipelineLexer
        implements ReportInputIntermediateStep<FlatReportEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatReportEvent model, long index) {
        if (model.getEndOfData()) {
            return;
        }

        DataRecordBuffer data = model.getData();
        int charsLength = data.charsLength;

        model.model.growTo(charsLength, 1);
    }
}
