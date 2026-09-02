package tech.kzen.auto.plugin.api.data

import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.ExecutionValue


/** A complete reader family contributed by the host or a third-party plugin. */
interface ReaderCapability {
    val identity: ReaderCapabilityIdentity

    fun decode(config: ExecutionValue): ReaderConfig

    fun validate(config: ReaderConfig)

    fun canonicalize(config: ReaderConfig): ReaderConfig

    fun encode(config: ReaderConfig): ExecutionValue

    fun requiredContent(config: ReaderConfig): ContentCapabilityIdentity

    suspend fun open(request: ReaderOpenRequest): DataCursor

    suspend fun inspect(request: ReaderInspectionRequest): DataShape
}
