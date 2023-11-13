package tech.kzen.auto.common.paradigm.common.v1.model

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath


object LogicConventions {
    // NB: referenced in logic-trace.yaml
    private val logicTraceStoreName = ObjectName("LogicTraceStore")

    private val logicTraceJvmPath = DocumentPath.parse(
        "auto-jvm/logic/logic-trace.yaml")


    val logicTraceStoreLocation = ObjectLocation(
        logicTraceJvmPath,
        ObjectPath(logicTraceStoreName, ObjectNesting.root))


    const val actionLookup = "lookup"
    const val actionMostRecent = "recent"
    const val actionReset = "reset"

    const val paramSubDocumentPath = "sub-path"
    const val paramSubObjectPath = "sub-object"

    const val paramQuery = "query"

    val parametersAttributeName = AttributeName("parameters")
    val parametersAttributePath = AttributePath.ofName(parametersAttributeName)


    //-----------------------------------------------------------------------------------------------------------------
    fun notRunningError(): String {
        return "Not running"
    }


    fun wrongRunningError(runId: LogicRunId, actualRunId: LogicRunId): String {
        return "Expected runId '${runId.value}' but was '${actualRunId.value}'"
    }


    fun missingExecution(executionId: LogicExecutionId, runId: LogicRunId): String {
        return "Execution '${executionId.value}' not found in run '${runId.value}'"
    }


    fun isMissingError(
        errorMessage: String,
        runId: LogicRunId,
        executionId: LogicExecutionId
    ): Boolean {
        return errorMessage == notRunningError() ||
                errorMessage.contains("'${runId.value}' but was") ||
                errorMessage.contains("'${executionId.value}' not found")
    }
}