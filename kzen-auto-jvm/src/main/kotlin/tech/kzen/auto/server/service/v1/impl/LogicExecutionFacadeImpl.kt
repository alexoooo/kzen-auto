package tech.kzen.auto.server.service.v1.impl

import kotlinx.datetime.Clock
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.server.objects.logic.LogicTraceStore
import tech.kzen.auto.server.service.v1.*
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator


class LogicExecutionFacadeImpl(
    private val graphDefinition: GraphDefinition,
    private val logicControl: LogicControl,
    private val listener: LogicExecutionListener
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
        val logicTraceHandle = LogicTraceStore.handle(runExecutionId, originalObjectLocation)

        val execution = dependencyInstance.execute(
            logicHandle,
            logicTraceHandle,
            runExecutionId,
            logicControl)
        logicExecution = execution
        return execution
    }


    override fun beforeStart(arguments: TupleValue): Boolean {
        return logicExecution!!.beforeStart(arguments)
    }


    override fun continueOrStart(): LogicResult {
        return logicExecution!!.continueOrStart(
            logicControl, graphDefinition)
    }


    override fun close() {
        logicExecution?.close(false)
    }
}