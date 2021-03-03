package tech.kzen.auto.plugin.model


interface ModelOutputEvent<T> {
    val type: Class<T>
    val model: T
    val skip: Boolean
}