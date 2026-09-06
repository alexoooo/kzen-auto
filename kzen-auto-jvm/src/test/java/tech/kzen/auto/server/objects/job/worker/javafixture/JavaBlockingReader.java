package tech.kzen.auto.server.objects.job.worker.javafixture;

import tech.kzen.auto.common.data.api.DataCursor;
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity;
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity;
import tech.kzen.auto.common.data.read.ReaderConfig;
import tech.kzen.auto.plugin.api.data.BlockingReaderCapability;
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest;
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest;
import tech.kzen.lib.common.exec.ExecutionValue;
import tech.kzen.lib.common.exec.MapExecutionValue;
import tech.kzen.lib.common.exec.data.shape.DataShape;
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance;
import tech.kzen.lib.common.exec.data.shape.ShapeStability;
import tech.kzen.lib.common.exec.data.value.DataValue;
import tech.kzen.lib.common.exec.data.value.LiteralDataValues;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


/** Plain-Java reader over the blocking base: each line of the input is one text record; no Continuation. */
public class JavaBlockingReader extends BlockingReaderCapability {
    public static final ReaderCapabilityIdentity IDENTITY = new ReaderCapabilityIdentity("java.fixture", "lines", "1");
    private static final ReaderConfig CONFIG = new ReaderConfig() {};

    @Override public ReaderCapabilityIdentity getIdentity() { return IDENTITY; }
    @Override public ReaderConfig decode(ExecutionValue config) { return CONFIG; }
    @Override public void validate(ReaderConfig config) {}
    @Override public ReaderConfig canonicalize(ReaderConfig config) { return CONFIG; }
    @Override public ExecutionValue encode(ReaderConfig config) { return new MapExecutionValue(Map.of()); }
    @Override public ContentCapabilityIdentity requiredContent(ReaderConfig config) {
        return ContentCapabilityIdentity.Companion.getSequentialBytes();
    }

    @Override
    public DataCursor openBlocking(ReaderOpenRequest request) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int read;
        while ((read = request.getBytes().read(buffer, 0, buffer.length)) != -1) {
            all.write(buffer, 0, read);
        }
        Iterator<String> lines = all.toString(StandardCharsets.UTF_8).lines().iterator();
        DataShape shape = new DataShape(LiteralDataValues.INSTANCE.lift("", null).getContract(),
                ShapeProvenance.Declared, ShapeStability.Stable.INSTANCE, List.of());
        return new DataCursor() {
            private boolean closed;
            @Override public DataShape getShape() { return shape; }
            @Override public boolean hasNext() { return !closed && lines.hasNext(); }
            @Override public DataValue next() {
                if (!hasNext()) throw new NoSuchElementException();
                return LiteralDataValues.INSTANCE.lift(lines.next(), null);
            }
            @Override public void close() { closed = true; }
        };
    }

    @Override
    public DataShape inspectBlocking(ReaderInspectionRequest request) {
        try (DataCursor cursor = openBlocking(request.getOpen())) {
            return cursor.getShape();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
