package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;


public class CsvPipelineParser
        implements PipelineIntermediateStep<FlatProcessorEvent>
{
//    //-----------------------------------------------------------------------------------------------------------------
//    private long count = 0;


    //-----------------------------------------------------------------------------------------------------------------
    @SuppressWarnings("EnhancedSwitchMigration")
    @Override
    public void process(FlatProcessorEvent model) {
//        if (++count == 794477) {
//            System.out.println("> " + count);
//        }

        RecordDataBuffer data = model.getData();
        FlatDataRecord flatDataRecord = model.model;
        flatDataRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatDataRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatDataRecord.fieldEndsUnsafe();

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
            flatDataRecord.indicateNonEmpty();
        }
        fieldEnds[nextFieldCount++] = nextFieldContentLength;
        flatDataRecord.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }
}
