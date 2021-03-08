package tech.kzen.auto.server.objects.report.pipeline.input.parse.text.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.DataFramer;
import tech.kzen.auto.plugin.model.DataBlockBuffer;
import tech.kzen.auto.plugin.model.DataFrameBuffer;

import java.util.Objects;


public class TextLineDataFramer
        implements DataFramer
{
    //-----------------------------------------------------------------------------------------------------------------
//    private boolean partial = true;
    private boolean midDelimiter = false;


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void frame(
            @NotNull DataBlockBuffer dataBlockBuffer
    ) {
        char[] chars = Objects.requireNonNull(dataBlockBuffer.chars);
        int charLength = dataBlockBuffer.charsLength;
        DataFrameBuffer frames = dataBlockBuffer.frames;

//        boolean nextPartial = partial;
        int startIndex = 0;
        if (midDelimiter) {
            startIndex++;
            midDelimiter = false;
        }

        int offset = startIndex;
        for (int i = startIndex; i < charLength; i++) {
            char nextChar = chars[i];

            switch (nextChar) {
                case '\r' -> {
                    int length = i - offset;
                    frames.add(offset, length);
                    i++;
                    offset = i + 1;
                    if (i == charLength) {
                        midDelimiter = true;
                    }
//                    nextPartial = false;
                }

                case '\n' -> {
                    int length = i - offset;
                    frames.add(offset, length);
                    offset = i + 1;
//                    nextPartial = false;
                }

//                default ->
//                        nextPartial = true;
            }
        }

        if (charLength - offset >= 0) {
            frames.add(offset, charLength - offset);
            frames.setPartialLast();
        }

//        partial = nextPartial;

        if (dataBlockBuffer.endOfData) {
            dataBlockBuffer.frames.clearPartialLast();
//            partial = false;
        }
    }
}
