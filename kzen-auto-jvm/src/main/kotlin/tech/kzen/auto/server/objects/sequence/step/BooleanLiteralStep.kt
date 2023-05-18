package tech.kzen.auto.server.objects.sequence.step

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BooleanLiteralStep(
    private val value: Boolean,
    private val selfLocation: ObjectLocation
):
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(BooleanLiteralStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val logicTracePath = LogicTracePath.ofObjectLocation(selfLocation)


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.ofMain(LogicType.boolean)
    }


    override fun continueOrStart(
        stepContext: StepContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, value)

        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.displayValue = ExecutionValue.of(value)
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }
}