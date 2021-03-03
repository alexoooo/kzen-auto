package tech.kzen.auto.plugin.api


interface PipelineIntermediateStep<T> {
    fun process(model: T)
}