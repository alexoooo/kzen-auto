package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.DataRecordBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TsvPipelineLexer
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @Override
    public void process(FlatProcessorEvent model) {
        DataRecordBuffer data = model.getData();

        char[] chars = data.chars;
        int charsLength = data.charsLength;

        int fieldCount = 0;

        for (int i = 0; i < charsLength; i++) {
            if (chars[i] == '\t') {
                fieldCount++;
            }
        }

        int contentLength = charsLength - fieldCount;
        model.model.growTo(contentLength, fieldCount);
    }
}
