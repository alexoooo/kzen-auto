package tech.kzen.auto.common.paradigm.logic

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath


@Suppress("ConstPropertyName")
object LogicConventions {
    // TODO: promote Trace to top-level concept (it's even in kzen-lib) instead of routing through Detached?

    // NB: referenced in logic-trace.yaml
    private val logicTraceEndpointName = ObjectName("LogicTraceEndpoint")

    private val logicTraceJvmPath = DocumentPath.parse(
        "auto-jvm/logic/logic-trace.yaml")


    val logicTraceEndpointLocation = ObjectLocation(
        logicTraceJvmPath,
        ObjectPath(logicTraceEndpointName, ObjectNesting.root))


    const val actionLookup = "lookup"
    const val actionLookupRun = "lookup-run"
    const val actionLookupRunHistory = "lookup-run-history"
    const val actionMostRecent = "recent"
    const val actionReset = "reset"

    const val paramSubDocumentPath = "sub-path"
    const val paramSubObjectPath = "sub-object"

    const val paramQuery = "query"
    const val paramSinceSequence = "since-sequence"

    val parametersAttributeName = AttributeName("parameters")
    val parametersAttributePath = AttributePath.ofName(parametersAttributeName)


    //-----------------------------------------------------------------------------------------------------------------
    fun runExecutionFromCollection(collection: Map<String, String>): LogicRunExecutionId {
        return LogicRunExecutionId(
            LogicRunId(collection[CommonRestApi.paramRunId]!!),
            LogicExecutionId(collection[CommonRestApi.paramExecutionId]!!)
        )
    }


    fun runExecutionAsCollection(logicRunExecutionId: LogicRunExecutionId): Map<String, String> {
        return mapOf(
            CommonRestApi.paramRunId to logicRunExecutionId.logicRunId.value,
            CommonRestApi.paramExecutionId to logicRunExecutionId.logicExecutionId.value
        )
    }


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