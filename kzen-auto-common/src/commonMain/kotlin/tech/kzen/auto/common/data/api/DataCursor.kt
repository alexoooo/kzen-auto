package tech.kzen.auto.common.data.api

import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * One single-pass open part: its owner drives each blocking pull and owns close.
 * A cursor captures no [DataContext], so a migrated owner can continue driving the same handle through its new
 * context. Every item is the canonical value described by [shape].
 */
interface DataCursor: Iterator<DataValue>, AutoCloseable {
    val shape: DataShape
}
