package tech.kzen.auto.common.data.api

import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Per-call environment for a source or opener. This is not a kzen Context declaration or registry entry; it is
 * the narrow path through which data code reaches named arguments and the caller-owned blocking boundary.
 */
interface DataContext {
    fun argument(name: String): Any?


    suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
        throw UnsupportedOperationException("Hosting data-source logic requires an active run")
    }


    suspend fun <R> blocking(block: () -> R): R
}
