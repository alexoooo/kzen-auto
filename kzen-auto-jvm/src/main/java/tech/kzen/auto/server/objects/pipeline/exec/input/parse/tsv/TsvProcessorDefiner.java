package tech.kzen.auto.server.objects.pipeline.exec.input.parse.tsv;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.definition.*;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.model.PluginCoordinate;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.pipeline.exec.input.ProcessorInputChain;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;
import tech.kzen.auto.server.objects.pipeline.exec.input.parse.common.FirstRecordItemHeaderExtractor;
import tech.kzen.auto.server.objects.pipeline.exec.input.parse.common.FlatPipelineHandoff;
import tech.kzen.auto.server.objects.pipeline.exec.input.parse.common.FlatProcessorEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class TsvProcessorDefiner
        implements ProcessorDefiner<FlatFileRecord>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ProcessorDefinitionInfo info = new ProcessorDefinitionInfo(
            new PluginCoordinate("TSV"),
            List.of("tsv"),
            dataEncoding,
            ProcessorDefinitionInfo.priorityAvoid);

//    private static final int ringBufferSize = 4 * 1024;
    private static final int ringBufferSize = 32 * 1024;


    public static final TsvProcessorDefiner instance = new TsvProcessorDefiner();


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
                FirstRecordItemHeaderExtractor::new);
    }


    private ProcessorDataDefinition<FlatFileRecord> defineData() {
        return new ProcessorDataDefinition<>(
                TsvLineDataFramer::new,
                FlatFileRecord.class,
                List.of(defineSegment()));
    }


    private ProcessorSegmentDefinition<FlatProcessorEvent, ModelOutputEvent<FlatFileRecord>> defineSegment() {
        return new ProcessorSegmentDefinition<>(
                FlatProcessorEvent::new,
                FlatFileRecord.class,
                List.of(
                        new ProcessorSegmentStepDefinition<>(List.of(
                            TsvPipelineLexer::new)),
                        new ProcessorSegmentStepDefinition<>(List.of(
                                TsvPipelineParser::new))
                ),
                () -> new FlatPipelineHandoff(true),
                ringBufferSize);
    }
}