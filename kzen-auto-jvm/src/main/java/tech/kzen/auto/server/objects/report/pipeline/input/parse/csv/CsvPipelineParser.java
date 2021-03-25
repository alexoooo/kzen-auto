package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class CsvPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
    @SuppressWarnings("EnhancedSwitchMigration")
    @Override
    public void process(FlatProcessorEvent model) {
        RecordDataBuffer data = model.getData();
        RecordRowBuffer recordRowBuffer = model.model;

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = recordRowBuffer.fieldContentsUnsafe();
        int[] fieldEnds = recordRowBuffer.fieldEndsUnsafe();

        int nextFieldCount = 0;
        int nextFieldContentLength = 0;
        int state = CsvFormatUtils.stateInitial;

        for (int i = 0; i < charsLength; i++) {
            char nextChar = contentChars[i];

            int prevState = state;
            state = CsvFormatUtils.nextFramedState(state, nextChar);

            switch (state) {
                case CsvFormatUtils.stateStartOfField: {
                    fieldEnds[nextFieldCount++] = nextFieldContentLength;
                    break;
                }

                case CsvFormatUtils.stateInQuoted: {
                    if (nextChar != '"' || prevState == CsvFormatUtils.stateInQuotedQuote) {
                        fieldContents[nextFieldContentLength++] = nextChar;
                    }
                    break;
                }

                case CsvFormatUtils.stateInUnquoted: {
                    fieldContents[nextFieldContentLength++] = nextChar;
                    break;
                }
            }
        }

        if (charsLength > 0) {
            recordRowBuffer.indicateNonEmpty();
        }
        fieldEnds[nextFieldCount++] = nextFieldContentLength;
        recordRowBuffer.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }
}