package tech.kzen.auto.common.paradigm.flow.api.output

import tech.kzen.lib.common.exec.data.type.DataContract

interface OptionalOutput<in T> {
    val contract: DataContract

    /**
     * Must be called at most (and in some cases exactly) one time.
     */
    fun set(payload: T)
}
