package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.api.managed.PipelineOutput


interface ReportTerminalStep<T, Output> {
    fun process(model: T, output: PipelineOutput<Output>)

    fun awaitEndOfData()
}