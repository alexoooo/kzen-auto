package tech.kzen.auto.common.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline


/**
 * A [DataSourceId] is durable rather than an `ObjectStableId`, whose identity is session-scoped.
 * Nothing mints one until a provider-bound source exists; see `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.3.
 */
@JvmInline
@Serializable
value class DataSourceId(
    val value: String
)
