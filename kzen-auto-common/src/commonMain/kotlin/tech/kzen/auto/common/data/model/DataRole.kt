package tech.kzen.auto.common.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline


/**
 * Open name describing the purpose a [DataPart] serves within a [DataUnit]; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.2.
 */
@JvmInline
@Serializable
value class DataRole(
    val name: String
) {
    companion object {
        val main = DataRole("main")
    }
}
