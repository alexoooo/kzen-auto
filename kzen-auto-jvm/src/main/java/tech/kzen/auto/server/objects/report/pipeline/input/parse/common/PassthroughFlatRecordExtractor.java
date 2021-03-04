package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;


import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.FlatRecordExtractor;
import tech.kzen.auto.plugin.model.FlatRecordBuilder;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer;


public class PassthroughFlatRecordExtractor
    implements FlatRecordExtractor<RecordRowBuffer>
{
    //-----------------------------------------------------------------------------------------------------------------
    public static final FlatRecordExtractor<RecordRowBuffer> instance = new PassthroughFlatRecordExtractor();


    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void extract(RecordRowBuffer recordRowBuffer, @NotNull FlatRecordBuilder builder) {
        ((RecordRowBuffer) builder).copy(recordRowBuffer);
    }
}
