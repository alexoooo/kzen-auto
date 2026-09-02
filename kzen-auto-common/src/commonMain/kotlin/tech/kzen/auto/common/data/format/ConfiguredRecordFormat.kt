package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.util.digest.Digestible


/** Resolved, immutable read configuration selected by a data source. */
interface ConfiguredRecordFormat: Digestible {
    val title: String
    val extensions: List<String>
    val catalogVisible: Boolean

    fun resolvedRead(ref: DataRef): ResolvedReadSpec

    fun declaredShape(): DataShape?
}
