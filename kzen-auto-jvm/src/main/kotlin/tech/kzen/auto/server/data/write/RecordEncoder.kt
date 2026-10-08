package tech.kzen.auto.server.data.write

import tech.kzen.auto.plugin.model.data.DataRecordBuffer


/**
 * Encodes records of one fixed column list, appending bytes to the byte side of a caller-supplied
 * [DataRecordBuffer] (`bytes` from `bytesLength`, grown as needed), so a reused buffer takes the next record without
 * allocating. A value that would not read back as itself through the same format fails by name, and nothing is
 * appended. Holds reusable state: one encoder per writer, never shared between threads.
 */
interface RecordEncoder {
    /** Appends what precedes an output's first record (a byte-order mark, a header row), which may be nothing. */
    fun encodeHeader(output: DataRecordBuffer)

    fun encodeRecord(cells: RecordCells, output: DataRecordBuffer)
}
