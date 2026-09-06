package tech.kzen.auto.server.objects.job.worker.javafixture;

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput;
import tech.kzen.auto.common.paradigm.job.control.JobControl;
import tech.kzen.auto.server.objects.job.worker.CursorSourceWorker;
import tech.kzen.lib.common.exec.data.value.DataValue;
import tech.kzen.lib.common.model.location.ObjectLocation;
import tech.kzen.lib.common.reflect.Reflect;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Plain-Java cursor source compiled by javac against the Kotlin base: opens a closeable iterator of
 * {@link Item}s and never touches a Continuation or an Emitter. Static counters let a test assert opens, pulls
 * and closes; the optional latches stage a cancellation while a pull is inside the blocking body.
 */
@Reflect
public class JavaCountingSource extends CursorSourceWorker {
    public static final AtomicInteger opened = new AtomicInteger();
    public static final AtomicInteger cursorsClosed = new AtomicInteger();
    public static final AtomicInteger itemsCreated = new AtomicInteger();
    public static final AtomicInteger itemsClosed = new AtomicInteger();
    public static volatile int count = 3;
    /** When set, the first pull signals {@link #pulled} after creating its item and then waits on {@link #proceed}. */
    public static volatile CountDownLatch pulled;
    public static volatile CountDownLatch proceed;
    public static volatile boolean failOpen;

    public static void reset() {
        opened.set(0);
        cursorsClosed.set(0);
        itemsCreated.set(0);
        itemsClosed.set(0);
        count = 3;
        pulled = null;
        proceed = null;
        failOpen = false;
    }

    public JavaCountingSource(ChannelOutput<DataValue> output, ObjectLocation selfLocation) {
        super(output, selfLocation);
    }

    @Override
    protected Iterator<?> open(JobControl control) {
        if (failOpen) {
            throw new IllegalStateException("open refused");
        }
        opened.incrementAndGet();
        return new Cursor(count);
    }

    /** A closeable element: what a host's arena-backed batch looks like. */
    public static final class Item implements AutoCloseable {
        public final int value;
        private boolean closed;

        Item(int value) {
            this.value = value;
            itemsCreated.incrementAndGet();
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                itemsClosed.incrementAndGet();
            }
        }

        public int value() {
            return value;
        }
    }

    private static final class Cursor implements Iterator<Item>, AutoCloseable {
        private final int limit;
        private int next = 0;

        Cursor(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean hasNext() {
            return next < limit;
        }

        @Override
        public Item next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Item item = new Item(next++);
            CountDownLatch signal = pulled;
            CountDownLatch gate = proceed;
            if (signal != null && item.value == 0) {
                signal.countDown();
                try {
                    gate.await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return item;
        }

        @Override
        public void close() {
            cursorsClosed.incrementAndGet();
        }
    }
}
