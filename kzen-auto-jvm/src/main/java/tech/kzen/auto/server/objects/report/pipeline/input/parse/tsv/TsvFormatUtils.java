package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv;


import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;


public enum TsvFormatUtils {;
    //-----------------------------------------------------------------------------------------------------------------
    public static final char delimiter = '\t';
    public static final int delimiterInt = delimiter;


    //-----------------------------------------------------------------------------------------------------------------
    public static String toTsv(FlatDataRecord flatDataRecord) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeTsv(flatDataRecord, writer);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }


    private static void writeTsv(FlatDataRecord flatDataRecord, Writer out) throws IOException {
        int fieldCount = flatDataRecord.fieldCount();
        int fieldContentLength = flatDataRecord.fieldContentLength();
        boolean nonEmpty = ! flatDataRecord.isEmpty();

        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            throw new IllegalStateException("Can't represent non-empty record with single empty column");
        }

        for (int i = 0; i < fieldCount; i++) {
            if (i != 0) {
                out.write(TsvFormatUtils.delimiterInt);
            }

            writeTsvField(flatDataRecord, i, out);
        }
    }


    private static void writeTsvField(
            FlatDataRecord flatDataRecord, int fieldIndex, Writer out
    ) throws IOException {
        int startIndex = flatDataRecord.contentStart(fieldIndex);
        int length = flatDataRecord.contentEnd(fieldIndex) - startIndex;
        out.write(flatDataRecord.fieldContentsUnsafe(), startIndex, length);
    }
}
