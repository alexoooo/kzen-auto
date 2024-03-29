package tech.kzen.auto.server.objects.report.exec.input.parse.csv;


import tech.kzen.auto.plugin.api.ReportIntermediateStep;
import tech.kzen.auto.plugin.model.data.DataRecordBuffer;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatReportEvent;


public class CsvPipelineLexer
        implements ReportIntermediateStep<FlatReportEvent>
{
//    //-----------------------------------------------------------------------------------------------------------------
//    private long count = 0;


    //-----------------------------------------------------------------------------------------------------------------
    @SuppressWarnings("EnhancedSwitchMigration")
    @Override
    public void process(FlatReportEvent model, long index) {
        if (model.getEndOfData()) {
            return;
        }

        DataRecordBuffer data = model.getData();
        char[] chars = data.chars;
        int charsLength = data.charsLength;

//        if (++count == 794477) {
//            System.out.println("> " + count + ") " + new String(chars, 0, charsLength));
//        }

        int fieldCount = 1;
        int contentLength = 0;
        int state = CsvFormatUtils.stateStartOfField;

        for (int i = 0; i < charsLength; i++) {
            char nextChar = chars[i];

            int previousState = state;
            state = CsvFormatUtils.nextFramedState(state, nextChar);

            switch (state) {
                case CsvFormatUtils.stateStartOfField: {
                    fieldCount++;
                    break;
                }

                case CsvFormatUtils.stateInQuoted: {
                    if (nextChar != '"' || previousState == CsvFormatUtils.stateInQuotedQuote) {
                        contentLength++;
                    }
                    break;
                }

                case CsvFormatUtils.stateInUnquoted: {
                    contentLength++;
                    break;
                }
            }
        }

        model.model.growTo(contentLength, fieldCount);
    }
}
