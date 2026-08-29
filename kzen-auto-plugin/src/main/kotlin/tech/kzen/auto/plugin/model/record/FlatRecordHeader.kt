package tech.kzen.auto.plugin.model.record

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.FieldId


/** Shared immutable field plan attached to every flat record using the same schema. */
class FlatRecordHeader(
    val contract: DataContract
) {
    private val indexByField: Map<FieldId, Int>
    private val contractByIndex: List<DataContract>

    init {
        val record = contract.structural as? DataType.Record
            ?: throw DataException(DataProblem(
                DataProblem.invalidContract,
                "Flat-record header requires a record contract"))
        indexByField = record.fields.mapIndexed { index, field -> field.id to index }.toMap()
        contractByIndex = record.fields.map { field ->
            contract.child(DataPathSegment.Field(field.id))
        }
    }

    fun indexOf(name: String, occurrence: Int): Int =
        indexByField[FieldId(name, occurrence)] ?: -1

    fun contractAt(index: Int): DataContract = contractByIndex[index]
}
