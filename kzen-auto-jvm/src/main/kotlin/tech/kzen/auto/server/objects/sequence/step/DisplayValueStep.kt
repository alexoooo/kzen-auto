package tech.kzen.auto.server.objects.sequence.step

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation,
    private val selfLocation: ObjectLocation
): SequenceStep {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DisplayValueStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val logicTracePath = LogicTracePath.ofObjectLocation(selfLocation)


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
//        return TupleDefinition.ofVoidWithDetail()
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        val step = stepContext.activeSequenceModel.steps[text]
        val value = step?.value

//        val frame = imperativeModel.findLast(text)
//        val state = frame?.states?.get(text.objectPath)
//        val result = state?.previous as? ExecutionSuccess
//        val value = result?.value ?: NullExecutionValue
//        logger.info("foo: {} - {}", text, value)

        val mainValue =
            if (value is List<*>) {
                val components = value.filterIsInstance<TupleComponentValue>()
                components.find { it.name == TupleComponentName.main }?.value
            }
            else {
                null
            }

        val executionValue =
            if (mainValue != null) {
                TextExecutionValue(mainValue.toString())
            }
            else {
                TextExecutionValue(value.toString())
            }

//        stepContext.logicTraceHandle.set(
//            logicTracePath,
//            executionValue)

        val activeModel = stepContext.activeSequenceModel.steps[selfLocation]!!
        activeModel.detail = executionValue
        stepContext.logicTraceHandle.set(
            logicTracePath,
            activeModel.trace().asExecutionValue())

//        return LogicResultSuccess(
//            TupleValue.ofVoidWithDetail(executionValue))
        return LogicResultSuccess(TupleValue.empty)
    }

//    override suspend fun perform(
//        imperativeModel: ImperativeModel,
//        graphInstance: GraphInstance
//    ): ExecutionResult {
//        val frame = imperativeModel.findLast(text)
//        val state = frame?.states?.get(text.objectPath)
//        val result = state?.previous as? ExecutionSuccess
//        val value = result?.value ?: NullExecutionValue
//
//        return ExecutionSuccess(
//            NullExecutionValue,
//            value)
//    }
}