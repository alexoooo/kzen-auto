package tech.kzen.auto.common.objects.document.report.summary

import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.task.model.TaskModel


data class TableSummary(
    val columnSummaries: Map<String, ColumnSummary>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = TableSummary(mapOf())


        fun fromCollection(collection: Map<String, Map<String, Any>>): TableSummary {
            return TableSummary(
                collection.mapValues { ColumnSummary.fromCollection(it.value) })
        }


        fun fromExecutionSuccess(result: ExecutionSuccess): TableSummary? {
            @Suppress("UNCHECKED_CAST")
            val resultValue =
                result.value.get() as? Map<String, Map<String, Any>>
                ?: return null

            return fromCollection(resultValue)
        }


        fun fromTaskModel(taskModel: TaskModel): TableSummary? {
            val result = taskModel.finalOrPartialResult() as? ExecutionSuccess
                ?: return null

            return fromExecutionSuccess(result)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return columnSummaries.isEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Map<String, Any>> {
        return columnSummaries.mapValues { it.value.toCollection() }
    }
}