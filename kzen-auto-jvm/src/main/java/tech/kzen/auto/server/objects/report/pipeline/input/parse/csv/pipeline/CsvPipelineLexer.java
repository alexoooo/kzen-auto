package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.pipeline;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class CsvPipelineLexer
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    //-----------------------------------------------------------------------------------------------------------------
    @SuppressWarnings("EnhancedSwitchMigration")
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        char[] chars = data.chars;
        int charsLength = data.charsLength;

        int fieldCount = 1;
        int contentLength = 0;
        int state = CsvStateMachine.stateStartOfField;

        for (int i = 0; i < charsLength; i++) {
            char nextChar = chars[i];

            int previousState = state;
            state = CsvStateMachine.nextFramedState(state, nextChar);

            switch (state) {
                case CsvStateMachine.stateStartOfField: {
                    fieldCount++;
                    break;
                }

                case CsvStateMachine.stateInQuoted: {
                    if (nextChar != '"' || previousState == CsvStateMachine.stateInQuotedQuote) {
                        contentLength++;
                    }
                    break;
                }

                case CsvStateMachine.stateInUnquoted: {
                    contentLength++;
                    break;
                }
            }
        }

        model.model.growTo(contentLength, fieldCount);
    }
}
