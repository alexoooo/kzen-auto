package tech.kzen.auto.common.objects.document.report.progress

import tech.kzen.auto.common.paradigm.task.model.TaskProgress


data class ReportProgress(
    val outputCount: Long,
    val inputs: Map<String, ReportFileProgress>
) {
    companion object {
        private const val outputCountKey = "output"
        private const val inputsKey = "inputs"


        val empty = ReportProgress(0, mapOf())


//        fun notStarted(filePaths: List<String>): ReportProgress {
//            return ReportProgress(
//                0,
//                filePaths.map { it to ReportFileProgress.notStarted }.toPersistentMap())
//        }

        @Suppress("UNCHECKED_CAST")
        fun fromTaskProgress(taskProgress: TaskProgress): ReportProgress {
            return fromCollection(taskProgress.value as Map<String, Any>)
        }

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ReportProgress {
            return ReportProgress(
                (collection[outputCountKey] as String).toLong(),
                (collection[inputsKey] as Map<String, Map<String, Any>>)
                    .mapValues { ReportFileProgress.fromCollection(it.value) }
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            outputCountKey to outputCount.toString(),
            inputsKey to inputs.mapValues { it.value.toCollection() }
        )
    }


    fun toTaskProgress(): TaskProgress {
        return TaskProgress.fromCollection(toCollection())
    }
}