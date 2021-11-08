package tech.kzen.auto.plugin.api


interface ReportInputIntermediateStep<T> {
    fun process(model: T, index: Long)
}