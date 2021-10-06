package tech.kzen.auto.server.objects.report.exec.input.parse.tsv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatProcessorEvent;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatProcessorEvent;


public class TsvPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatProcessorEvent model, long index) {
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

        int nextFieldCount = 0;
        int nextFieldContentLength = 0;

        for (int i = 0; i < charsLength; i++) {
            char nextChar = contentChars[i];

            if (nextChar == '\t') {
                fieldEnds[nextFieldCount++] = nextFieldContentLength;
            }
            else {
                fieldContents[nextFieldContentLength++] = nextChar;
            }
        }

        fieldEnds[nextFieldCount++] = nextFieldContentLength;
        flatFileRecord.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }
}
