package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * The `${…}` file-name templates of `Write` and `Format`: each placeholder is a dotted path into the value's
 * metadata (`${name}`, `${parent.name}`), unless the Worker gives that name a meaning of its own.
 */
object FileNameTemplate {
    val placeholder = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_.]*)}")


    /** The text of the scalar at the dotted [path] of [metadata]: empty for a null scalar, null when absent. */
    fun metadataText(metadata: DataValue?, path: String): String? {
        var current = metadata ?: return null
        for (segment in path.split('.')) {
            val record = current.type as? DataType.Record
                ?: return null
            if (record.fields.none { it.id == FieldId(segment) }) {
                return null
            }
            val node = current.access.field(current.root, FieldId(segment))
            if (current.access.state(node) != DataState.Present) {
                return ""
            }
            current = DataValue(current.access, node)
        }
        if (current.type !is DataType.Scalar) {
            return null
        }
        return JobDataValues.boundary(current)?.toString() ?: ""
    }


    /**
     * Whether a metadata record of [contract] has a scalar at the dotted [path], as [metadataText] reads it; true
     * where the contract is dynamic, which only a run can tell.
     */
    fun metadataHas(contract: DataContract?, path: String): Boolean {
        var current = contract ?: return false
        for (segment in path.split('.')) {
            val record = when (val type = current.structural) {
                is DataType.Dynamic -> return true
                is DataType.Record -> type
                else -> return false
            }
            val field = FieldId(segment)
            if (record.fields.none { it.id == field }) {
                return false
            }
            current = current.child(DataPathSegment.Field(field))
        }
        return current.structural is DataType.Scalar || current.structural is DataType.Dynamic
    }
}
