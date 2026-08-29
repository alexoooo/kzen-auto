package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue


// TODO: enforce optional/required contracts
class MutableRequiredInput<out T>(
    override val contract: DataContract = DataContract(
        tech.kzen.lib.common.exec.data.type.DataType.Dynamic(nullable = true)),
    private val structural: Boolean = false
): RequiredInput<T>, MutableInput<T> {
    private var value: Any? = null
    private var present = false


    override fun get(): T {
        if (!present) {
            throw NoSuchElementException("required input missing")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }


    override fun set(value: DataValue, nativeProjection: Any?) {
        this.value = if (structural) value else nativeProjection
        present = true
    }


    override fun clear() {
        value = null
        present = false
    }
}
