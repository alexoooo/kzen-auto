package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicDefinition
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * A graph of concurrently-running Workers connected by named Channels, run on the kzen-lib Logic/Execution
 * model so it Runs / Steps / Pauses / Resumes through the shared
 * [tech.kzen.auto.server.service.impl.ServerLogicController] like a Script or Flow. `workers` / `channels`
 * autowire from nested document structure (NestedList, like Script's `steps`); the run itself is a
 * [JobExecution].
 */
@Reflect
class JobDocument(
    private val workers: List<ObjectLocation>,
    private val channels: List<ObjectLocation>,
    private val selfLocation: ObjectLocation,

    @Service private val objectStableMapper: ObjectStableMapper,
    @Service private val graphCreator: GraphCreator,
    @Service private val environment: GraphEnvironment
):
    DocumentArchetype(),
    Logic
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        // M1: no declared parameters / results yet (input parameters and harvested output channels are
        // later milestones), so the external Logic signature is empty — like a Script with none.
        return LogicDefinition(
            TupleDefinition(listOf()),
            TupleDefinition(listOf()))
    }


    override fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        return JobExecution(
            selfLocation.documentPath,
            workers,
            channels,
            logicTraceHandle,
            objectStableMapper,
            graphCreator,
            environment)
    }
}
