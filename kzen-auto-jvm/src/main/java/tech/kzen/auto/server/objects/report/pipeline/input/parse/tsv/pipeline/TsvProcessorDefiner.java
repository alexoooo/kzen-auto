package tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.definition.ProcessorDefiner;
import tech.kzen.auto.plugin.definition.ProcessorDefinition;
import tech.kzen.auto.plugin.definition.ProcessorDefinitionInfo;
import tech.kzen.auto.plugin.definition.ProcessorSegmentDefinition;
import tech.kzen.auto.plugin.spec.DataEncodingSpec;
import tech.kzen.auto.plugin.spec.TextEncodingSpec;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FirstRecordItemHeaderExtractor;
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FlatProcessorEvent;

import java.util.List;


public class TsvProcessorDefiner
        implements ProcessorDefiner<RecordItemBuffer>
{
    //-----------------------------------------------------------------------------------------------------------------
    private static final DataEncodingSpec dataEncoding = new DataEncodingSpec(
            new TextEncodingSpec(null));

    private static final ProcessorDefinitionInfo info = new ProcessorDefinitionInfo(
            "TSV",
            List.of("tsv"),
            dataEncoding);


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ProcessorDefinitionInfo info() {
        return info;
    }


    //-----------------------------------------------------------------------------------------------------------------
    @NotNull
    @Override
    public ProcessorDefinition<RecordItemBuffer> define() {
        return new ProcessorDefinition<>(
                TsvDataFramer::new,
                FlatProcessorEvent::new,
                FirstRecordItemHeaderExtractor::new,
                null,
                List.of(defineSegment()));
    }


    private ProcessorSegmentDefinition<FlatProcessorEvent, RecordItemBuffer> defineSegment() {
        return new ProcessorSegmentDefinition<>(
                FlatProcessorEvent.class,
                RecordItemBuffer.class,
                FlatProcessorEvent::new,
                List.of(
                        TsvPipelineLexer::new,
                        TsvPipelineParser::new
                ),
                TsvPipelineHandoff::new);
    }
}
