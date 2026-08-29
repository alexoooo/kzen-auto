package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.auto.common.paradigm.flow.api.input.OptionalInput
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue



// TODO: enforce optional/required contracts
class MutableOptionalInput<out T>(
    override val contract: DataContract = DataContract(
        tech.kzen.lib.common.exec.data.type.DataType.Dynamic(nullable = true)),
    private val structural: Boolean = false
): OptionalInput<T>, MutableInput<T> {
    private var value: Any? = null


    override fun get(): T? {
        @Suppress("UNCHECKED_CAST")
        return value as T?
    }


    override fun set(value: DataValue, nativeProjection: Any?) {
        this.value = if (structural) value else nativeProjection
    }


    override fun clear() {
        value = null
    }
}
