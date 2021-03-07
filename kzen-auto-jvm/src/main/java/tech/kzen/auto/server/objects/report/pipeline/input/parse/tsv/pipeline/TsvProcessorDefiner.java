package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.definition.*;
import tech.kzen.auto.plugin.model.ModelOutputEvent;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FirstRecordItemHeaderExtractor;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.PassthroughFlatRecordExtractor;
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorInputChain;
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorInputReader;
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.InputStreamFlatDataReader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class TsvProcessorDefiner
        implements ProcessorDefiner<RecordRowBuffer>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ProcessorDefinitionInfo info = new ProcessorDefinitionInfo(
            "TSV",
            List.of("tsv"),
            dataEncoding);


    public static final TsvProcessorDefiner instance = new TsvProcessorDefiner();


    //-----------------------------------------------------------------------------------------------------------------
    public static List<RecordRowBuffer> literal(String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        return literal(textBytes, StandardCharsets.UTF_8, textBytes.length);
    }


    public static List<RecordRowBuffer> literal(String text, int dataBlockSize) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        return literal(textBytes, StandardCharsets.UTF_8, dataBlockSize);
    }


    public static List<RecordRowBuffer> literal(
            byte[] textBytes, Charset charset, int dataBlockSize
    ) {
        ProcessorInputChain<RecordRowBuffer> chain = new ProcessorInputChain<>(
                new ProcessorInputReader(
                        InputStreamFlatDataReader.Companion.ofLiteral(textBytes),
                        true,
                        null),
                instance.defineData(),
                charset,
                dataBlockSize);

        List<RecordRowBuffer> builder = new ArrayList<>();

        chain.forEachModel(i -> builder.add(i.prototype()));

        return builder;
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
    public ProcessorDefinition<RecordRowBuffer> define() {
        return new ProcessorDefinition<>(
                defineData(),
                FirstRecordItemHeaderExtractor::new,
                () -> PassthroughFlatRecordExtractor.instance);
    }


    private ProcessorDataDefinition<RecordRowBuffer> defineData() {
        return new ProcessorDataDefinition<>(
                TsvDataFramer::new,
                RecordRowBuffer.class,
                List.of(defineSegment()));
    }


    private ProcessorSegmentDefinition<FlatProcessorEvent, ModelOutputEvent<RecordRowBuffer>> defineSegment() {
        return new ProcessorSegmentDefinition<>(
                FlatProcessorEvent::new,
                RecordRowBuffer.class,
                List.of(
                        TsvPipelineLexer::new,
                        TsvPipelineParser::new
                ),
                TsvPipelineHandoff::new);
    }
}
