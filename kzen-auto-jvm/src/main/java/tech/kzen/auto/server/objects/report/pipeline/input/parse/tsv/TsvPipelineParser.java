package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TsvPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        FlatDataRecord flatDataRecord = model.model;
        flatDataRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatDataRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatDataRecord.fieldEndsUnsafe();

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
        flatDataRecord.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }
}
