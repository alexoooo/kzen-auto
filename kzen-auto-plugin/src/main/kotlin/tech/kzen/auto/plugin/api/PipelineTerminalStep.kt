package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.api.managed.PipelineOutput


interface PipelineTerminalStep<T, Output> {
    fun process(model: T, output: PipelineOutput<Output>)

    //fun endOfStream(output: PipelineOutput<Output>)
}