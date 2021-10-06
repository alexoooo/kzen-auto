package tech.kzen.auto.server.objects.report.exec.input.parse.tsv;


import tech.kzen.auto.plugin.model.record.FlatFileRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;


public enum TsvFormatUtils {;
    //-----------------------------------------------------------------------------------------------------------------
    public static final char delimiter = '\t';
    public static final int delimiterInt = delimiter;


    //-----------------------------------------------------------------------------------------------------------------
    public static String toTsv(FlatFileRecord flatFileRecord) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeTsv(flatFileRecord, writer);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }


    private static void writeTsv(FlatFileRecord flatFileRecord, Writer out) throws IOException {
        int fieldCount = flatFileRecord.fieldCount();
        int fieldContentLength = flatFileRecord.fieldContentLength();
        boolean nonEmpty = ! flatFileRecord.isEmpty();

        if (fieldCount == 1 && fieldContentLength == 0 && nonEmpty) {
            throw new IllegalStateException("Can't represent non-empty record with single empty column");
        }

        for (int i = 0; i < fieldCount; i++) {
            if (i != 0) {
                out.write(TsvFormatUtils.delimiterInt);
            }

            writeTsvField(flatFileRecord, i, out);
        }
    }


    private static void writeTsvField(
            FlatFileRecord flatFileRecord, int fieldIndex, Writer out
    ) throws IOException {
        int startIndex = flatFileRecord.contentStart(fieldIndex);
        int length = flatFileRecord.contentEnd(fieldIndex) - startIndex;
        out.write(flatFileRecord.fieldContentsUnsafe(), startIndex, length);
    }
}
