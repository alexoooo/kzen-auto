package tech.kzen.auto.server.objects.report.pipeline.input.parse.text;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.HeaderExtractor;
import tech.kzen.auto.plugin.definition.*;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.model.PluginCoordinate;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.report.pipeline.input.ProcessorInputChain;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatPipelineHandoff;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class TextProcessorDefiner
        implements ProcessorDefiner<FlatFileRecord>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ProcessorDefinitionInfo info = new ProcessorDefinitionInfo(
            new PluginCoordinate("Text"),
            List.of("txt"),
            dataEncoding,
            99);

    private static final int ringBufferSize = 4 * 1024;


    public static final TextProcessorDefiner instance = new TextProcessorDefiner();

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
        return ProcessorInputChain.Companion.readAll(
                textBytes,
                instance.defineData(),
                FlatFileRecord::prototype,
                charset,
                dataBlockSize);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ProcessorDefinitionInfo info() {
        return info;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ProcessorDefinition<FlatFileRecord> define() {
        return new ProcessorDefinition<>(
                defineData(),
                () -> headerExtractor);
    }


    private ProcessorDataDefinition<FlatFileRecord> defineData() {
        return new ProcessorDataDefinition<>(
                TextLineDataFramer::new,
                FlatFileRecord.class,
                List.of(defineSegment()));
    }


    private ProcessorSegmentDefinition<FlatProcessorEvent, ModelOutputEvent<FlatFileRecord>> defineSegment() {
        return new ProcessorSegmentDefinition<>(
                FlatProcessorEvent::new,
                FlatFileRecord.class,
                List.of(
                        new ProcessorSegmentStepDefinition<>(List.of(
                            TextPipelineLexer::new)),
                        new ProcessorSegmentStepDefinition<>(List.of(
                            TextPipelineParser::new))
                ),
                () -> new FlatPipelineHandoff(false),
                ringBufferSize);
    }
}
