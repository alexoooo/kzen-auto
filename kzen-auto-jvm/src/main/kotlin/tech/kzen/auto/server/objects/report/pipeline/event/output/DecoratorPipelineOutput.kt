package tech.kzen.auto.server.objects.report.pipeline.event.output

import tech.kzen.auto.plugin.api.managed.PipelineOutput


class DecoratorPipelineOutput<T>(
    private val delegate: PipelineOutput<T>,
    private val preProcessor: (T) -> Unit
): PipelineOutput<T> {
    private var next: T? = null
    private val wrapper = ProcessorWrapper()


    override fun next(): T {
        next = delegate.next()
        preProcessor(next!!)
        return next!!
    }


    override fun commit() {
        delegate.commit()
    }


    override fun batch(size: Int, processor: (T) -> Unit) {
        wrapper.processor = processor
        delegate.batch(size, wrapper)
    }


    private inner class ProcessorWrapper: (T) -> Unit {
        var processor: (T) -> Unit = {}

        override fun invoke(item: T) {
            preProcessor(item)
            processor(item)
        }
    }
}