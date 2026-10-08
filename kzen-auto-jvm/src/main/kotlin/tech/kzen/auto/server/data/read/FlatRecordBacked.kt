package tech.kzen.auto.server.data.read

import tech.kzen.auto.plugin.model.record.FlatFileRecord


/**
 * A record access that reads its present fields from [flatRecord], field token `n + 1` being its field `n`, and
 * adds only the fields' states (null, absent). A present field's text there is its scalar's canonical text, so a
 * writer reads it in place, without a string per field.
 */
interface FlatRecordBacked {
    val flatRecord: FlatFileRecord
}
