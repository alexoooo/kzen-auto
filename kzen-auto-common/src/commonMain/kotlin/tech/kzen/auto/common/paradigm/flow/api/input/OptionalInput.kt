package tech.kzen.auto.common.paradigm.flow.api.input

import tech.kzen.lib.common.exec.data.type.DataContract

interface OptionalInput<out T> {
    val contract: DataContract

    /**
     * @return current received message payload (if any)
     */
    fun get(): T?
}
