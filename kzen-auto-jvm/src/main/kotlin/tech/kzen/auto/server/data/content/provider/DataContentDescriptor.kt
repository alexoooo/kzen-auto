package tech.kzen.auto.server.data.content.provider

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity


data class DataContentDescriptor(
    val ref: DataRef,
    val capabilities: Set<ContentCapabilityIdentity>
)
