package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.DataFramer;
import tech.kzen.auto.plugin.model.DataBlockBuffer;
import tech.kzen.auto.plugin.model.DataFrameBuffer;

import java.util.Objects;


public class TsvDataFramer
        implements DataFramer
{
    //-----------------------------------------------------------------------------------------------------------------
    private boolean partial = false;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void frame(
            @NotNull DataBlockBuffer dataBlockBuffer
    ) {
        char[] chars = Objects.requireNonNull(dataBlockBuffer.chars);
        int charLength = dataBlockBuffer.charsLength;

        DataFrameBuffer frames = dataBlockBuffer.frames;
        boolean nextPartial = partial;

        int offset = 0;
        for (int i = 0; i < charLength; i++) {
            char nextChar = chars[i];

            if (nextChar > 13) { // NB: max of (lineFeed, carriageReturn)
                nextPartial = true;
            }
            else {
                switch (nextChar) {
                    case '\r' -> {
                        frames.add(offset, i - offset);
                        i++;
                        offset = i;
                        nextPartial = false;
                    }

                    case '\n' -> {
                        frames.add(offset, i - offset);
                        offset = i;
                        nextPartial = false;
                    }

                    default ->
                            nextPartial = false;
                }
            }
        }

        if (nextPartial) {
            frames.add(offset, charLength - offset - 1);
            frames.setPartialLast();
        }
        partial = nextPartial;

        if (dataBlockBuffer.endOfData) {
            dataBlockBuffer.frames.clearPartialLast();
            partial = false;
        }
    }
}
