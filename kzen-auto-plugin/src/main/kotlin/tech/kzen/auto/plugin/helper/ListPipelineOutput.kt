package tech.kzen.auto.plugin.helper

import tech.kzen.auto.plugin.api.managed.PipelineOutput


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