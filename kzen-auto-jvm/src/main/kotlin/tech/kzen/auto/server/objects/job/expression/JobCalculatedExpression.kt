package tech.kzen.auto.server.objects.job.expression

import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue


/** Generated contract-native expression over one Job value and its optional static projection. */
interface JobCalculatedExpression {
    /**
     * Whether [evaluate] reads its `model`, the value's payload as an object. When false the expression never
     * names it, so a caller passes null rather than materializing a record's fields as a map for every value.
     */
    val readsPayload: Boolean

    fun evaluate(model: Any?, value: DataValue, projection: ColumnProjection?): Any?
    fun setParameters(values: List<Any?>) {}
}


/** [JobCalculatedExpression.evaluate] given [value]'s payload object, materialized only when it is read. */
internal fun JobCalculatedExpression.evaluate(value: DataValue, projection: ColumnProjection?): Any? =
    evaluate(if (readsPayload) JobDataValues.native(value) else null, value, projection)
