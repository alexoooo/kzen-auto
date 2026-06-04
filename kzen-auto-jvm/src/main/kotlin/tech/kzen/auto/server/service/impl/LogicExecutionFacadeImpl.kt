package tech.kzen.auto.server.service.impl

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.*
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


class LogicExecutionFacadeImpl(
    private val graphDefinition: GraphDefinition,
    private val logicControl: LogicControl,
    private val listener: LogicExecutionListener,
    private val logicTraceStore: LogicTraceStore,
    private val environment: () -> GraphEnvironment
):
    LogicExecutionFacade
{
    //-----------------------------------------------------------------------------------------------------------------
    private var logicExecution: LogicExecution? = null


    fun open(
        runExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation,
        logicHandle: LogicHandle,
        graphCreator: GraphCreator
    ): LogicExecution {
        val dependencyGraphInstance = graphCreator.createGraph(
            graphDefinition.filterTransitive(originalObjectLocation), environment())

        val dependencyInstance = dependencyGraphInstance
            .objectInstances[originalObjectLocation]
            ?.reference as? Logic
            ?: throw IllegalArgumentException("Dependency logic not found: $originalObjectLocation")

        val logicTraceHandle = logicTraceStore.handle(runExecutionId, originalObjectLocation)

        val execution = dependencyInstance.execute(
            logicHandle,
            logicTraceHandle,
            runExecutionId,
            logicControl)
        logicExecution = execution
        return execution
    }


    override fun beforeStart(arguments: TupleValue): Boolean {
        return logicExecution!!.beforeStart(arguments/*, false*/)
    }


    override fun continueOrStart(graphDefinition: GraphDefinition): LogicResult {
        // Use the live definition passed down from the enclosing pass (not the start-time snapshot
        // captured in the constructor field, which is only used by open() for first instantiation),
        // so editing a failed step inside this sub-script takes effect on resume.
        return logicExecution!!.continueOrStart(
            logicControl, graphDefinition)
    }


    override fun close() {
        logicExecution?.close(false)
        listener.closed()
    }
}