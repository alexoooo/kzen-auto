package tech.kzen.auto.server.objects.sequence.step

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleDefinition
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation
): SequenceStep {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DisplayValueStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.ofVoidWithDetail()
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        val step = stepContext.activeSequenceModel.steps[text]
        val value = step?.value ?: 0

//        val frame = imperativeModel.findLast(text)
//        val state = frame?.states?.get(text.objectPath)
//        val result = state?.previous as? ExecutionSuccess
//        val value = result?.value ?: NullExecutionValue
        logger.info("foo: {} - {}", text, value)

        val executionValue = TextExecutionValue(value.toString())

        return LogicResultSuccess(
            TupleValue.ofVoidWithDetail(executionValue))
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