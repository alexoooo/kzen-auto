package tech.kzen.auto.server.data.content.provider

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.server.data.content.policy.ContentReadControl


interface DataContentProvider {
    suspend fun describe(context: DataContext, ref: DataRef, control: ContentReadControl): DataContentDescriptor

    /** The provider owns every acquired resource until a handle is returned and closes it on every failed return. */
    suspend fun acquire(context: DataContext, ref: DataRef, control: ContentReadControl): DataContentHandle
}
