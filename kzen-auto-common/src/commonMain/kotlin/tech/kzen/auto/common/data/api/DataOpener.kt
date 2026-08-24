package tech.kzen.auto.common.data.api

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.DataShape


/**
 * Opens concrete data parts independently of the source that resolved them. [open] is suspend because opening
 * may require [DataContext.blocking], such as a JDBC connection; the returned cursor is a handle driven by its
 * owner rather than capturing that context. An implementation that acquires the cursor inside a cancellable
 * blocking handoff must close it if cancellation wins after acquisition but before [open] returns ownership.
 */
interface DataOpener {
    suspend fun open(context: DataContext, part: DataPart): DataCursor


    suspend fun inspectShape(context: DataContext, part: DataPart): DataShape? = null
}
