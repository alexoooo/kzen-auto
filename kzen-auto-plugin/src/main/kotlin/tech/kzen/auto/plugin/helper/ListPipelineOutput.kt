package tech.kzen.auto.plugin.helper

import tech.kzen.auto.plugin.api.managed.PipelineOutput


/**
 * Synchronous, single-threaded [PipelineOutput] backed by a growing list of
 * pre-allocated slots. Producer fills slots via [next]/[commit]; consumer
 * drains via [flush], which resets the write cursor but retains the
 * allocated slots for reuse across flushes (zero-realloc steady state).
 *
 * **Not thread-safe.** The backing list and write cursor are unsynchronized.
 * Intended for in-process drain-after-fill use within a single call — see
 * `tech.kzen.auto.server.objects.report.exec.input.ReportInputChain` for the
 * canonical pattern (one segment buffer per ring-stage, filled by the
 * producer-side step and drained immediately before recursing into the next
 * segment, all on the calling thread).
 *
 * For multi-threaded producer/consumer pipelines, use
 * `tech.kzen.auto.server.objects.report.exec.event.output.DisruptorPipelineOutput`
 * — its ring-buffer sequence ordering provides the happens-before
 * relationship this class lacks.
 */
class ListPipelineOutput<T>(
    private val factory: () -> T
):
    PipelineOutput<T>
{
    //-----------------------------------------------------------------------------------------------------------------
    private val buffer = mutableListOf<T>()
    private var nextIndex = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun next(): T {
        return when {
            buffer.size <= nextIndex -> {
                val next = factory()
                buffer.add(next)
                next
            }

            else ->
                buffer[nextIndex]
        }
    }


    override fun commit() {
        nextIndex++
    }


    override fun batch(size: Int, processor: (T) -> Unit) {
        for (i in 0 until size) {
            val next = next()
            processor(next)
            commit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun flush(consumer: (T) -> Unit) {
        for (i in 0 until nextIndex) {
            consumer(buffer[i])
        }
        nextIndex = 0
    }
}