package tech.kzen.auto.plugin.api.managed


interface PipelineOutput<T> {
    fun next(): T
    fun commit()

    fun batch(size: Int, processor: (T) -> Unit)
}