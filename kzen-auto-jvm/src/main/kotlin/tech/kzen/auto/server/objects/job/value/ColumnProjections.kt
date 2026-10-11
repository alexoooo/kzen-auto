package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.read.FlatRecordBacked
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * The [ColumnProjection]s of the values one Worker reads, one at a time, as [JobDataValues.projection] makes them,
 * without its per-value work: while the payload contract is the same object (a reader's records share their header's),
 * the descriptor is found and checked once, and for a flat record the column nodes are resolved once rather than by
 * name per read. Confined to its Worker's coroutine.
 */
class ColumnProjections {
    private var contract: DataContract? = null
    private var descriptor: ColumnProjectionDescriptor? = null

    // Resolved from the first flat record of the contract; null when its columns are not all root fields
    private var flatResolved = false
    private var flatNodes: Array<DataNode>? = null


    fun of(value: DataValue): ColumnProjection {
        val payload = value.payloadContract
        if (payload.structural is DataType.Mapping) {
            return JobDataValues.projection(value)
        }

        var known = descriptor
        if (known == null || payload !== contract) {
            known = ColumnProjectionDescriptor.from(payload)
            known.check(value)
            contract = payload
            descriptor = known
            flatResolved = false
        }

        val access = value.access
        if (access !is FlatFileRecord && access !is FlatRecordBacked) {
            return ColumnProjection(known, value, null)
        }
        if (!flatResolved) {
            flatNodes = known.rootFieldNodes(value)
            flatResolved = true
        }
        return ColumnProjection(known, value, flatNodes)
    }
}
