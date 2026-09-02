package tech.kzen.auto.server.objects.job.expression

import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.lib.common.exec.data.value.DataValue


/** Generated contract-native expression over one Job value and its optional static projection. */
interface JobCalculatedExpression<T> {
    fun evaluate(model: T, value: DataValue, projection: ColumnProjection?): Any?
    fun setParameters(values: List<Any?>) {}
}
