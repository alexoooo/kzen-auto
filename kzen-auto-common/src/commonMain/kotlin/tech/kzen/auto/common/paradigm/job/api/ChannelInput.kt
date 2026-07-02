package tech.kzen.auto.common.paradigm.job.api


/**
 * Consumer endpoint of a one-way Job Channel. A Worker reads single logical ELEMENTS; the framework transparently
 * UN-batches the physical chunks the producer sent. A framework base Worker drains a whole physical chunk at a
 * time via [receiveChunk] (so a cooperative [tech.kzen.auto.common.paradigm.job.control.JobControl.checkpoint]
 * lands per chunk, not per element — one step ≈ one chunk), then hands the chunk's elements to its work hook one
 * by one; a raw Worker may instead read element-by-element via [receive] or `for (x in input) { … }`. All three
 * terminate at end-of-stream — signalled once all producer endpoints have closed (close-on-last-producer).
 */
interface ChannelInput<out T> {
    /**
     * Suspends until the next element is available.
     *
     * @return the next element, or null at end-of-stream. NB: a stream MAY legitimately carry a null element
     *   (the untyped scalar / Run lane), so a base Worker reads whole chunks via [receiveChunk] to disambiguate;
     *   [receive] is a convenience for raw Workers over never-null lanes.
     */
    suspend fun receive(): T?


    /**
     * Suspends until the next physical chunk is available.
     *
     * @return the next chunk of elements (never empty; individual elements may be null), or null at
     *   end-of-stream. This is the framework-facing drain: [tech.kzen.auto.server.objects.job.worker.SinkWorker]
     *   / `TransformWorker` checkpoint once per chunk, then dispatch its elements to `onElement`.
     */
    suspend fun receiveChunk(): List<T>?


    /**
     * Suspending iterator backing `for (x in input) { … }`; iterates elements until end-of-stream.
     */
    operator fun iterator(): ChannelInputIterator<T>
}
