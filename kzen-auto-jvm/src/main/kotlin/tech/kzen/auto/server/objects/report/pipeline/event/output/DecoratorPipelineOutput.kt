package tech.kzen.auto.server.objects.report.pipeline.event.output

import tech.kzen.auto.plugin.api.managed.PipelineOutput


class DecoratorPipelineOutput<T>(
    private val delegate: PipelineOutput<T>,
    private val postProcessor: (T) -> Unit
): PipelineOutput<T> {
    private var next: T? = null

    override fun next(): T {
        next = delegate.next()
        return next!!
    }

    override fun commit() {
        postProcessor(next!!)
        delegate.commit()
    }
}