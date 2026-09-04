package tech.kzen.auto.server.data.read.text

import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue


class PlainTextReader(
    private val input: SequentialCharacterContent,
    policy: ReadOperationalPolicy,
    private val checkpoint: () -> Unit = {}
): AutoCloseable {
    companion object {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("line", 0), DataType.Scalar(ScalarKind.Text)))))
    }

    private val maximumLineCharacters = minOf(
        policy.maximumRecordCharacters ?: Int.MAX_VALUE,
        policy.maximumFieldCharacters ?: Int.MAX_VALUE)
    private val buffer = CharArray(8192)
    private var bufferSize = 0
    private var bufferIndex = 0
    private var pending = -1
    private var closed = false


    fun read(): DataValue? {
        check(!closed) { "Plain-text reader is closed" }
        val line = StringBuilder()
        while (true) {
            val next = readCharacter()
            if (next < 0) return if (line.isEmpty()) null else value(line.toString())
            when (val character = next.toChar()) {
                '\n' -> return value(line.toString())
                '\r' -> {
                    val following = readCharacter()
                    if (following >= 0 && following != '\n'.code) pending = following
                    return value(line.toString())
                }
                else -> {
                    line.append(character)
                    if (line.length > maximumLineCharacters) {
                        throw IllegalArgumentException(
                            "Plain-text line exceeds character limit $maximumLineCharacters")
                    }
                    if (line.length and 1023 == 0) checkpoint()
                }
            }
        }
    }


    private fun readCharacter(): Int {
        if (pending >= 0) return pending.also { pending = -1 }
        while (bufferIndex >= bufferSize) {
            bufferSize = input.read(buffer)
            bufferIndex = 0
            if (bufferSize < 0) return -1
            if (bufferSize == 0) continue
        }
        return buffer[bufferIndex++].code
    }


    private fun value(line: String): DataValue {
        val record = FlatFileRecord.of(listOf(line))
        record.attachHeader(FlatRecordHeader(contract))
        return DataValue(record, DataNode(0))
    }


    override fun close() {
        if (closed) return
        closed = true
        input.close()
    }
}
