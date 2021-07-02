package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.HeaderExtractor;
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput;
import tech.kzen.auto.plugin.model.record.FlatFileRecord;

import java.util.List;
import java.util.Objects;


public class FirstRecordItemHeaderExtractor
        implements HeaderExtractor<FlatFileRecord>
{
    @NotNull
    @Override
    public List<String> extract(
            @NotNull TraversableProcessorOutput<FlatFileRecord> processed
    ) {
        @SuppressWarnings("unchecked")
        List<String>[] values = new List[1];

        processed.poll(event -> {
            if (values[0] == null) {
                FlatFileRecord row = event.getModel();
                Objects.requireNonNull(row);
                values[0] = row.toList();
            }
        });

        return Objects.requireNonNullElse(values[0], List.of());
    }
}
