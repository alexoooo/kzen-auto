package tech.kzen.auto.server.service.impl

import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.*
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import kotlin.time.Clock


class LogicExecutionFacadeImpl(
    private val graphDefinition: GraphDefinition,
    private val logicControl: LogicControl,
    private val listener: LogicExecutionListener,
    private val logicTraceStore: LogicTraceStore
):
    LogicExecutionFacade
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val clock = Clock.System
        private val random = java.util.Random(42)

        @Volatile
        private var previous = clock.now()

        fun arbitraryId(): String {
            val now = clock.now()
            if (now != previous) {
                previous = now
                return now.toString()
            }

            val randomSuffix = random.nextLong()
            return "${now}_${randomSuffix.toULong()}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var logicExecution: LogicExecution? = null


    fun open(
        logicRunId: LogicRunId,
        originalObjectLocation: ObjectLocation,
        logicHandle: LogicHandle,
        graphCreator: GraphCreator
    ): LogicExecution {
        val dependencyGraphInstance = graphCreator.createGraph(
            graphDefinition.filterTransitive(originalObjectLocation))

        val dependencyInstance = dependencyGraphInstance
            .objectInstances[originalObjectLocation]
            ?.reference as? Logic
            ?: throw IllegalArgumentException("Dependency logic not found: $originalObjectLocation")

        val logicExecutionId = LogicExecutionId(arbitraryId())
        val runExecutionId = LogicRunExecutionId(logicRunId, logicExecutionId)
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


    override fun continueOrStart(): LogicResult {
        return logicExecution!!.continueOrStart(
            logicControl, graphDefinition)
    }


    override fun close() {
        logicExecution?.close(false)
        listener.closed()
    }
}