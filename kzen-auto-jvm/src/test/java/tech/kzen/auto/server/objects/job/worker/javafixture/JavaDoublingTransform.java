package tech.kzen.auto.server.objects.job.worker.javafixture;

import tech.kzen.auto.common.paradigm.job.api.ChannelInput;
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput;
import tech.kzen.auto.common.paradigm.job.control.JobControl;
import tech.kzen.auto.server.objects.job.worker.JavaTransformWorker;
import tech.kzen.lib.common.exec.data.value.DataValue;
import tech.kzen.lib.common.model.location.ObjectLocation;
import tech.kzen.lib.common.reflect.Reflect;

import java.util.Iterator;
import java.util.List;


/**
 * Plain-Java transform over the Kotlin base: per element two outputs (the value and its double), and one
 * trailing total at completion — stateful across the stream, no Continuation, no Emitter.
 */
@Reflect
public class JavaDoublingTransform extends JavaTransformWorker {
    private long total;

    public JavaDoublingTransform(ChannelInput<?> input, ChannelOutput<DataValue> output, ObjectLocation selfLocation) {
        super(input, output, selfLocation);
    }

    @Override
    protected Iterator<?> onElementBlocking(Object element, JobControl control) {
        int value = ((JavaCountingSource.Item) element).value();
        total += value;
        return List.of(value, value * 2).iterator();
    }

    @Override
    protected Iterator<?> onCompleteBlocking(JobControl control) {
        return List.of("total=" + total).iterator();
    }
}
