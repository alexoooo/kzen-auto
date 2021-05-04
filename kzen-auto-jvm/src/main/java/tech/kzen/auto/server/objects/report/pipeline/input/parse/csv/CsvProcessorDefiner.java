package tech.kzen.auto.server.objects.report.pipeline.input.parse.csv;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.definition.*;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.model.PluginCoordinate;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.report.pipeline.input.ProcessorInputChain;
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FirstRecordItemHeaderExtractor;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatPipelineHandoff;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class CsvProcessorDefiner
        implements ProcessorDefiner<FlatFileRecord>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ProcessorDefinitionInfo info = new ProcessorDefinitionInfo(
            new PluginCoordinate("CSV"),
            List.of("csv"),
            dataEncoding,
            ProcessorDefinitionInfo.priorityAvoid);

//    private static final int ringBufferSize = 4 * 1024;
    private static final int ringBufferSize = 32 * 1024;


    public static final CsvProcessorDefiner instance = new CsvProcessorDefiner();


    //-----------------------------------------------------------------------------------------------------------------
    public static List<FlatFileRecord> literal(String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        return literal(textBytes, StandardCharsets.UTF_8, textBytes.length);
    }


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
                FirstRecordItemHeaderExtractor::new);
    }


    private ProcessorDataDefinition<FlatFileRecord> defineData() {
        return new ProcessorDataDefinition<>(
                CsvDataFramer::new,
                FlatFileRecord.class,
                List.of(defineSegment()));
    }


    private ProcessorSegmentDefinition<FlatProcessorEvent, ModelOutputEvent<FlatFileRecord>> defineSegment() {
        return new ProcessorSegmentDefinition<>(
                FlatProcessorEvent::new,
                FlatFileRecord.class,
                List.of(
                        CsvPipelineLexer::new,
                        CsvPipelineParser::new
                ),
                () -> new FlatPipelineHandoff(true),
                ringBufferSize);
    }
}
