package tech.kzen.auto.plugin.api


interface ReportIntermediateStep<T> {
    fun process(model: T, index: Long)
}