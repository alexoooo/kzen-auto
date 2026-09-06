package tech.kzen.auto.server.exec.job.ownership

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream


/**
 * Stream fixtures an expression source can return by qualified name (the expression class path sees the test
 * classes): a `java.util.stream.Stream` counting its `close()`, an object that is both the iterator and the
 * closeable container, and lists of [CloseCountingResource] elements. Static counters let a test assert how
 * many times a stream was evaluated and closed across a live-edit migration.
 */
object ClosingStreams {
    val evaluations = AtomicInteger()
    val streamCloses = AtomicInteger()
    val elements = CopyOnWriteArrayList<CloseCountingResource>()


    fun reset() {
        evaluations.set(0)
        streamCloses.set(0)
        elements.clear()
    }


    /** A closeable stream of scalars: closed once by the run when the source lets it go. */
    @JvmStatic
    fun stream(count: Int): Stream<String> {
        evaluations.incrementAndGet()
        return Stream.iterate(0) { it + 1 }.limit(count.toLong()).map { "s$it" }
            .onClose { streamCloses.incrementAndGet() }
    }


    /** A closeable stream of closeable elements. */
    @JvmStatic
    fun resourceStream(count: Int): Stream<CloseCountingResource> {
        evaluations.incrementAndGet()
        return Stream.iterate(0) { it + 1 }.limit(count.toLong()).map { resource("r$it") }
            .onClose { streamCloses.incrementAndGet() }
    }


    /**
     * A plain (non-closeable) iterable of closeable elements, constructed as they are pulled: re-evaluated and
     * skipped across a migration. (An eagerly built collection's never-pulled tail stays the expression's own,
     * as any resource acquired before the stream is returned.)
     */
    @JvmStatic
    fun resources(count: Int): Iterable<CloseCountingResource> {
        evaluations.incrementAndGet()
        return Iterable { (0 until count).asSequence().map { resource("l$it") }.iterator() }
    }


    /** An iterator that is its own closeable container: closed exactly once. */
    @JvmStatic
    fun selfClosingIterator(count: Int): Iterator<String> {
        evaluations.incrementAndGet()
        return SelfClosingIterator(count)
    }


    private fun resource(name: String): CloseCountingResource =
        CloseCountingResource(name).also { elements += it }


    private class SelfClosingIterator(private val count: Int): Iterator<String>, AutoCloseable {
        private var next = 0

        override fun hasNext(): Boolean = next < count

        override fun next(): String = "i${next++}"

        override fun close() {
            streamCloses.incrementAndGet()
        }
    }
}
