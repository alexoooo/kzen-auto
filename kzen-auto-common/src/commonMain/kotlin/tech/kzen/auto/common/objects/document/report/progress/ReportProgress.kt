package tech.kzen.auto.common.objects.document.report.progress

import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.common.util.data.DataLocation


data class ReportProgress(
    val outputCount: Long,
    val inputs: Map<DataLocation, ReportFileProgress>
) {
    companion object {
        private const val outputCountKey = "output"
        private const val inputsKey = "inputs"


        val empty = ReportProgress(0, mapOf())


        @Suppress("UNCHECKED_CAST")
        fun fromTaskProgress(taskProgress: TaskProgress): ReportProgress {
            return fromCollection(taskProgress.value as Map<String, Any>)
        }

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ReportProgress {
            return ReportProgress(
                (collection[outputCountKey] as String).toLong(),
                (collection[inputsKey] as Map<String, Map<String, Any>>)
                    .map { DataLocation.of(it.key) to ReportFileProgress.fromCollection(it.value) }
                    .toMap()
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            outputCountKey to outputCount.toString(),
            inputsKey to inputs.map { it.key.asString() to it.value.toCollection() }.toMap()
        )
    }


    fun toTaskProgress(): TaskProgress {
        return TaskProgress.fromCollection(toCollection())
    }
}