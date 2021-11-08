package tech.kzen.auto.server.objects.report.exec.input.parse.text;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.HeaderExtractor;
import tech.kzen.auto.plugin.definition.*;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.model.PluginCoordinate;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.report.exec.input.ReportInputChain;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatPipelineHandoff;
import tech.kzen.auto.server.objects.report.exec.input.parse.common.FlatReportEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class TextReportDefiner
        implements ReportDefiner<FlatFileRecord>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ReportDefinitionInfo info = new ReportDefinitionInfo(
            new PluginCoordinate("Text"),
            List.of("txt"),
            dataEncoding,
            99);

    private static final int ringBufferSize = 4 * 1024;


    public static final TextReportDefiner instance = new TextReportDefiner();

    public static final String textHeader = "Text";
    public static final HeaderExtractor<FlatFileRecord> headerExtractor =
            HeaderExtractor.Companion.ofLiteral(textHeader);


    //-----------------------------------------------------------------------------------------------------------------
//    public static List<RecordRowBuffer> literal(String text) {
//        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
//        return literal(textBytes, StandardCharsets.UTF_8, textBytes.length);
//    }


    public static List<FlatFileRecord> literal(String text, int dataBlockSize) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        return literal(textBytes, StandardCharsets.UTF_8, dataBlockSize);
    }


    public static List<FlatFileRecord> literal(
            byte[] textBytes, Charset charset, int dataBlockSize
    ) {
        return ReportInputChain.Companion.readAll(
                textBytes,
                instance.defineData(),
                FlatFileRecord::prototype,
                charset,
                dataBlockSize);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ReportDefinitionInfo info() {
        return info;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ReportDefinition<FlatFileRecord> define() {
        return new ReportDefinition<>(
                defineData(),
                () -> headerExtractor);
    }


    private ReportDataDefinition<FlatFileRecord> defineData() {
        return new ReportDataDefinition<>(
                TextLineDataFramer::new,
                FlatFileRecord.class,
                List.of(defineSegment()));
    }


    private ReportSegmentDefinition<FlatReportEvent, ModelOutputEvent<FlatFileRecord>> defineSegment() {
        return new ReportSegmentDefinition<>(
                FlatReportEvent::new,
                FlatFileRecord.class,
                List.of(
                        new ReportSegmentStepDefinition<>(List.of(
                            TextPipelineLexer::new)),
                        new ReportSegmentStepDefinition<>(List.of(
                            TextPipelineParser::new))
                ),
                () -> new FlatPipelineHandoff(false),
                ringBufferSize);
    }
}
