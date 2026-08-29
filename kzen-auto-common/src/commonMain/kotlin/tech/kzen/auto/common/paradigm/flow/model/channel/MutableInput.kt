package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue

interface MutableInput<out T> {
    val contract: DataContract

    fun set(value: DataValue, nativeProjection: Any?)

    fun clear()
}
