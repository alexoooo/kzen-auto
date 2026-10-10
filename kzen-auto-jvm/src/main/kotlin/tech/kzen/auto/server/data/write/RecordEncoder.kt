package tech.kzen.auto.server.data.write

import tech.kzen.auto.plugin.model.data.DataRecordBuffer


/**
 * Encodes records of one fixed column list, appending bytes to the byte side of a caller-supplied
 * [DataRecordBuffer] (`bytes` from `bytesLength`, grown as needed), so a reused buffer takes the next record without
 * allocating. A value that would not read back as itself through the same format fails by name, and nothing is
 * appended. Holds reusable state: one encoder per writer, never shared between threads.
 *
 * One encoder writes any number of outputs, interleaved: what one output needs remembered is its
 * [RecordOutputState], from [openOutput], passed back with each of its calls.
 */
interface RecordEncoder {
    /** The state of a new output, before its header. */
    fun openOutput(): RecordOutputState

    /** Appends what precedes an output's first record (a byte-order mark, a header row), which may be nothing. */
    fun encodeHeader(state: RecordOutputState, output: DataRecordBuffer)

    fun encodeRecord(state: RecordOutputState, cells: RecordCells, output: DataRecordBuffer)

    /** Appends what follows an output's last record (a closing bracket), which may be nothing. */
    fun encodeFooter(state: RecordOutputState, output: DataRecordBuffer)
}
