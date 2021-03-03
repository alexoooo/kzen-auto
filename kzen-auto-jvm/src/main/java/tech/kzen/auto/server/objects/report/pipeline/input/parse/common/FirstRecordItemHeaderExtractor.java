package tech.kzen.auto.server.objects.report.pipeline.input.parse.common;

import org.jetbrains.annotations.NotNull;
import tech.kzen.auto.plugin.api.HeaderExtractor;
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput;
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer;

import java.util.List;
import java.util.Objects;


public class FirstRecordItemHeaderExtractor implements HeaderExtractor<RecordItemBuffer> {
    @NotNull
    @Override
    public List<String> extract(@NotNull TraversableProcessorOutput<RecordItemBuffer> processed) {
        @SuppressWarnings("unchecked")
        List<String>[] values = new List[1];

        processed.poll(event -> {
            if (values[0] == null) {
                values[0] = event.getModel().toList();
            }
        });

        return Objects.requireNonNullElse(values[0], List.of());
    }
}
