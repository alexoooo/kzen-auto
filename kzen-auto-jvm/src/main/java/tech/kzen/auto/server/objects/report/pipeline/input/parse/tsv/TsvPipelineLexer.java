package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class TsvPipelineLexer
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
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
