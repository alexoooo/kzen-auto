package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import tech.kzen.auto.plugin.api.PipelineIntermediateStep;
import tech.kzen.auto.plugin.model.RecordDataBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord;
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
        if (model.getEndOfData()) {
            return;
        }

//        if (++count == 794477) {
//            System.out.println("> " + count);
//        }

        RecordDataBuffer data = model.getData();
        FlatFileRecord flatFileRecord = model.model;
        flatFileRecord.clearCache();

        char[] contentChars = data.chars;
        int charsLength = data.charsLength;

        char[] fieldContents = flatFileRecord.fieldContentsUnsafe();
        int[] fieldEnds = flatFileRecord.fieldEndsUnsafe();

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
            flatFileRecord.indicateNonEmpty();
        }
        fieldEnds[nextFieldCount++] = nextFieldContentLength;
        flatFileRecord.setCountAndLengthUnsafe(nextFieldCount, nextFieldContentLength);
    }
}
