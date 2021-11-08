package tech.kzen.auto.server.objects.report.exec.input.parse.tsv;


import tech.kzen.auto.plugin.api.ReportInputIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatReportEvent;


public class TsvPipelineLexer
        implements ReportInputIntermediateStep<FlatReportEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatReportEvent model, long index) {
        if (model.getEndOfData()) {
            return;
        }

        DataRecordBuffer data = model.getData();
        char[] chars = data.chars;
        int charsLength = data.charsLength;

        int fieldCount = 1;

        for (int i = 0; i < charsLength; i++) {
            if (chars[i] == '\t') {
                fieldCount++;
            }
        }

        int contentLength = charsLength - fieldCount + 1;
        model.model.growTo(contentLength, fieldCount);
    }
}
