package tech.kzen.auto.common.data.api

import tech.kzen.auto.common.data.schema.DataShape


/**
 * One single-pass open part, following `CsvRecordReader`: its owner drives each blocking pull and owns close.
 * A cursor captures no [DataContext], so a migrated owner can continue driving the same handle through its new
 * context. A tabular [shape] means every item is a `FlatFileRecord` under that header.
 */
interface DataCursor: Iterator<Any?>, AutoCloseable {
    val shape: DataShape?
}
