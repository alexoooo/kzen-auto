package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId


data class OutputInfo(
    val runDir: String,
    val table: OutputTableInfo?,
    val export: OutputExportInfo?,
    val status: OutputStatus,
    val runExecutionId: LogicRunExecutionId?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val runDirKey = "work"
        private const val statusKey = "status"
        private const val tableKey = "table"
        private const val exportKey = "export"
        private const val runIdKey = "run"
        private const val executionIdKey = "exec"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
//            println("^^^ OutputInfo ## fromCollection - $collection")

            @Suppress("UNCHECKED_CAST")
            val tableMap = collection[tableKey] as? Map<String, Any?>
            val table = tableMap?.let {
                OutputTableInfo.fromCollection(it)
            }

            @Suppress("UNCHECKED_CAST")
            val exportMap = collection[exportKey] as? Map<String, Any?>
            val export = exportMap?.let {
                OutputExportInfo.fromCollection(it)
            }

            val status = OutputStatus.valueOf(
                collection[statusKey] as String)

            val runId = (collection[runIdKey] as? String)?.let { LogicRunId(it) }
            val executionId = (collection[executionIdKey] as? String)?.let { LogicExecutionId(it) }

            val runExecutionId =
                if (runId != null && executionId != null) {
                    LogicRunExecutionId(runId, executionId)
                }
                else {
                    null
                }

            return OutputInfo(
                collection[runDirKey] as String,
                table,
                export,
                status,
                runExecutionId)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        val builder = mutableMapOf<String, Any?>()
        builder[runDirKey] = runDir

        if (table != null) {
            builder[tableKey] = table.toCollection()
        }
        
        if (export != null) {
            builder[exportKey] = export.toCollection()
        }

        builder[statusKey] = status.name

        if (runExecutionId != null) {
            builder[runIdKey] = runExecutionId.logicRunId.value
            builder[executionIdKey] = runExecutionId.logicExecutionId.value
        }

        return builder
    }
}