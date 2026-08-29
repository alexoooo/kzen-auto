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
    const val actionLookupRunExecutions = "lookup-run-executions"
    const val actionMostRecent = "recent"
    const val actionTraced = "traced"
    const val actionReset = "reset"
    const val actionResetAll = "reset-all"

    const val paramSubDocumentPath = "sub-path"
    const val paramSubObjectPath = "sub-object"

    const val paramQuery = "query"
    const val paramSinceSequence = "since-sequence"

    // The `parameters` branch of typed ParameterBinding declarations, shared by every Logic flavour that
    // declares one (Script and Job); ScriptConventions aliases these so script-side code reads naturally.
    val parametersAttributeName = AttributeName("parameters")
    val parametersAttributePath = AttributePath.ofName(parametersAttributeName)

    // The declared result signature: a `results` map (component name -> TypeMetadata) parsed by
    // ResultSignatureDefiner into the output binding schema — plain data on the main object, shared by
    // every Logic flavour that declares one (Script and Job); empty/absent => void.
    val resultsAttributeName = AttributeName("results")
    val resultsAttributePath = AttributePath.ofName(resultsAttributeName)


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
        return wrongRunningPrefix(runId) + "'${actualRunId.value}'"
    }


    // The half of wrongRunningError a caller can reconstruct: it knows the run it asked for, not the one that
    // replaced it.
    private fun wrongRunningPrefix(runId: LogicRunId): String {
        return "Expected runId '${runId.value}' but was "
    }


    /**
     * Did the request fail because the *run* the caller addressed is gone — replaced or no longer active while a
     * client was still projecting it — rather than because the request itself was bad? A caller uses this to stay
     * quiet through a teardown instead of surfacing an error the user cannot act on.
     *
     * Each arm reconstructs a message THIS object builds instead of matching prose out of one, so a reworded
     * message moves the check with it. That is as structural as the seam gets: the failure crosses the wire as
     * `ExecutionFailure`, which carries a message and no code.
     *
     * Scope is deliberately the run, not the individual execution. A request addressed to a node that has
     * vanished fails with kzen-lib `RunEngine`'s "No request handler for node: …", which is NOT treated as
     * missing: that same message is what a genuinely unregistered handler produces, and swallowing it would
     * hide a wiring defect behind a teardown that may not be happening.
     */
    fun isMissingError(
        errorMessage: String,
        runId: LogicRunId
    ): Boolean {
        return errorMessage == notRunningError() ||
                errorMessage.startsWith(wrongRunningPrefix(runId))
    }
}
