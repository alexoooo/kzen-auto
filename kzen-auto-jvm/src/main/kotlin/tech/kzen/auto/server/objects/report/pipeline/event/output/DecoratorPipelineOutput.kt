package tech.kzen.auto.server.objects.report.pipeline.event.output

import tech.kzen.auto.plugin.api.managed.PipelineOutput


class DecoratorPipelineOutput<T>(
    private val delegate: PipelineOutput<T>,
    private val preProcessor: (T) -> Unit
): PipelineOutput<T> {
    private var next: T? = null

    override fun next(): T {
        next = delegate.next()
        preProcessor(next!!)
        return next!!
    }

    override fun commit() {
        delegate.commit()
    }
}