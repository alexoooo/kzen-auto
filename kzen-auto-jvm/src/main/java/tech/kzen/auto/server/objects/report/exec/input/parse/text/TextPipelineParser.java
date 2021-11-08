package tech.kzen.auto.server.objects.report.exec.input.parse.text;


import tech.kzen.auto.plugin.api.ReportInputIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatReportEvent;


public class TextPipelineParser
        implements ReportInputIntermediateStep<FlatReportEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatReportEvent model, long index) {
        if (model.getEndOfData()) {
            return;
        }

        DataRecordBuffer data = model.getData();
        FlatFileRecord flatFileRecord = model.model;
        flatFileRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatFileRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatFileRecord.fieldEndsUnsafe();

        System.arraycopy(contentChars, 0, fieldContents, 0, charsLength);

        fieldEnds[0] = charsLength;
        flatFileRecord.setCountAndLengthUnsafe(1, charsLength);
    }
}
