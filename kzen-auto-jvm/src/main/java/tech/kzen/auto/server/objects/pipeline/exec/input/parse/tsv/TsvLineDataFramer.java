package tech.kzen.auto.server.objects.pipeline.exec.input.parse.tsv;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.DataFramer;
import tech.kzen.auto.plugin.model.data.DataBlockBuffer;
import tech.kzen.auto.plugin.model.data.DataFrameBuffer;

import java.util.Objects;


public class TsvLineDataFramer
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

            if (nextChar > 13) { // max of (lineFeed, carriageReturn)
                nextPartial = true;
            }
            else {
                switch (nextChar) {
                    case '\r' -> {
                        int length = i - offset;
                        if (length > 0 || nextPartial) {
                            frames.add(offset, i - offset);
                        }
                        i++;
                        offset = i + 1;
                        nextPartial = false;
                    }

                    case '\n' -> {
                        int length = i - offset;
                        if (length > 0 || nextPartial) {
                            frames.add(offset, i - offset);
                        }
                        offset = i + 1;
                        nextPartial = false;
                    }

                    default ->
                            nextPartial = true;
                }
            }
        }

        if (nextPartial) {
            frames.add(offset, charLength - offset);
            frames.setPartialLast();
        }
        partial = nextPartial;

        if (dataBlockBuffer.endOfData) {
            dataBlockBuffer.frames.clearPartialLast();
            partial = false;
        }
    }
}
