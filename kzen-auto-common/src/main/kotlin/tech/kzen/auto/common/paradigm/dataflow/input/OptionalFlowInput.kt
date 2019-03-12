package tech.kzen.auto.common.paradigm.dataflow.input


interface OptionalFlowInput<out T> {
    /**
     * @return current received message payload (if any)
     */
    fun get(): T?

//    fun index(): Long
//    fun isRepeated(): Boolean
}