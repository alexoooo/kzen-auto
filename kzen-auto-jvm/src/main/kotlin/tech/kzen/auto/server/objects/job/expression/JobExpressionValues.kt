package tech.kzen.auto.server.objects.job.expression

import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import java.math.BigDecimal


/** Exact structural value bridge used by generated Job expressions. */
object JobExpressionValues {
    fun projected(projection: ColumnProjection, ordinal: Int): Any? {
        return when (projection.state(ordinal)) {
            DataState.Absent, DataState.Null -> null
            DataState.Present -> JobDataValues.boundary(DataValue(
                projection.value.access,
                projection.node(ordinal)))
        }
    }


    fun keyed(value: DataValue, key: String): Any? {
        val node = when (value.contract.structural) {
            is DataType.Record -> value.access.field(value.root, FieldId(key))
            is DataType.Mapping,
            is DataType.Dynamic -> value.access.entry(value.root, TextExecutionValue(key))
            else -> error("Keyed access requires a record, mapping, or dynamic value")
        }
        return when (value.access.state(node)) {
            DataState.Absent, DataState.Null -> null
            DataState.Present -> JobDataValues.boundary(DataValue(value.access, node))
        }
    }


    fun scalar(value: Any?, type: DataType.Scalar): Pair<DataState, ScalarExecutionValue?> {
        if (value == null) {
            require(type.nullable) { "Non-null calculated field returned null" }
            return DataState.Null to null
        }
        val kind = type.kind
        val encoded: ScalarExecutionValue = when (kind) {
            ScalarKind.Boolean -> BooleanExecutionValue(value as Boolean)
            is ScalarKind.Integer -> LongExecutionValue(integer(value, kind))
            ScalarKind.Decimal -> TextExecutionValue((value as BigDecimal).stripTrailingZeros().let {
                if (it.signum() == 0) "0" else it.toString()
            })
            is ScalarKind.Floating -> NumberExecutionValue((value as Number).toDouble())
            ScalarKind.Binary -> BinaryExecutionValue(value as ByteArray)
            ScalarKind.Text,
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid -> TextExecutionValue(value.toString())
        }
        return DataState.Present to encoded
    }


    private fun integer(value: Any, kind: ScalarKind.Integer): Long =
        if (kind.signed) {
            (value as Number).toLong()
        }
        else when (kind.bits) {
            8 -> (value as UByte).toLong()
            16 -> (value as UShort).toLong()
            32 -> (value as UInt).toLong()
            else -> error("Job expression boundary does not support unsigned 64-bit integers")
        }
}
