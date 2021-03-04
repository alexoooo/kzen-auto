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


    public static final ProcessorDefiner<RecordRowBuffer> instance = new TsvProcessorDefiner();


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
                new ProcessorDataDefinition<>(
                        TsvDataFramer::new,
                        RecordRowBuffer.class,
                        List.of(defineSegment())
                ),
                FirstRecordItemHeaderExtractor::new,
                () -> PassthroughFlatRecordExtractor.instance);
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
