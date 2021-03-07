package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.DataFramer;
import tech.kzen.auto.plugin.model.DataBlockBuffer;
import tech.kzen.auto.plugin.model.DataFrameBuffer;

import java.util.Objects;


public class CsvDataFramer
        implements DataFramer
{
    //-----------------------------------------------------------------------------------------------------------------
    private boolean partial = false;
    private boolean midDelimiter = false;
    private int state = CsvStateMachine.stateInitial;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void frame(
            @NotNull DataBlockBuffer dataBlockBuffer
    ) {
        char[] contentChars = Objects.requireNonNull(dataBlockBuffer.chars);
        int charLength = dataBlockBuffer.charsLength;
        DataFrameBuffer frames = dataBlockBuffer.frames;

        int contentStart = 0;
        if (midDelimiter) {
            contentStart++;
            midDelimiter = false;
        }

        boolean nextPartial = partial;
        int nextState = state;

        int startOffset = contentStart;

        for (int i = contentStart; i < charLength; i++) {
            char nextChar = contentChars[i];

            nextState = CsvStateMachine.nextState(nextState, nextChar);

            if (nextState == CsvStateMachine.stateEndOfRecord) {
                frames.add(startOffset, i - startOffset);

                if (nextChar == '\r') {
                    i++;
                    if (i == charLength) {
                        midDelimiter = true;
                    }
                }

                startOffset = i + 1;
                nextPartial = false;
            }
            else {
                nextPartial = true;
            }
        }

        if (nextPartial) {
            frames.add(startOffset, charLength - startOffset);
            frames.setPartialLast();
        }

        state = nextState;
        partial = nextPartial;

        if (dataBlockBuffer.endOfData) {
            dataBlockBuffer.frames.clearPartialLast();
            partial = false;
            midDelimiter = false;
            state = CsvStateMachine.stateInitial;
        }
    }
}
