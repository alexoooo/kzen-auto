package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue


internal fun testJobValue(element: Any?): DataValue =
    element as? DataValue
        ?: error("Expected DataValue, found ${element?.let { it::class.qualifiedName } ?: "null"}")


internal fun testProjection(element: Any?): ColumnProjection =
    JobDataValues.projection(testJobValue(element))


internal fun testRecord(element: Any?): FlatFileRecord {
    val projection = testProjection(element)
    return JobDataValues.record(projection)
}


internal fun testBoundary(element: Any?): Any? =
    JobDataValues.boundary(testJobValue(element))
