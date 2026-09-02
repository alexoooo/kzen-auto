package tech.kzen.auto.common.data.schema

import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AuthoredRecordSchema(
    val fields: DataSchemaFieldListSpec
): RecordSchema {
    override fun contract(): DataContract {
        val dataFields = mutableListOf<DataField>()
        val nativeByPath = mutableMapOf<DataTypePath, tech.kzen.lib.common.model.structure.metadata.TypeMetadata>()
        for ((name, spec) in fields.fields) {
            val id = FieldId(name)
            val fieldContract = spec.typeMetadata.toDataContract()
            dataFields += DataField(id, fieldContract.structural)
            for ((path, metadata) in fieldContract.nativeByPath) {
                nativeByPath[DataTypePath(listOf(DataPathSegment.Field(id)) + path.segments)] = metadata
            }
        }
        return DataContract(DataType.Record(dataFields), nativeByPath)
    }
}
