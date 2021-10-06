package tech.kzen.auto.server.objects.report.exec.input.parse.text;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatProcessorEvent;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatProcessorEvent;


public class TextPipelineLexer
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void process(FlatProcessorEvent model, long index) {
        if (model.getEndOfData()) {
            return;
        }

        DataRecordBuffer data = model.getData();
        int charsLength = data.charsLength;

        model.model.growTo(charsLength, 1);
    }
}
