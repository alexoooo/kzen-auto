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
 * Plain-Java transform emitting one Java record per element and declaring that record class as its output —
 * so the design-time walk shows the record's columns before any run, and the run lifts each record under the
 * same described shape.
 */
@Reflect
public class JavaRecordTransform extends JavaTransformWorker {
    /** A plain Java record: the shape the walk describes by its components, in order. */
    public record Summary(long value, String label) {}


    public JavaRecordTransform(ChannelInput<?> input, ChannelOutput<DataValue> output, ObjectLocation selfLocation) {
        super(input, output, selfLocation);
    }


    @Override
    protected Iterator<?> onElementBlocking(Object element, JobControl control) {
        int value = ((JavaCountingSource.Item) element).value();
        return List.of(new Summary(value, value % 2 == 0 ? "even" : "odd")).iterator();
    }


    @Override
    protected Class<?> outputClass() {
        return Summary.class;
    }
}
