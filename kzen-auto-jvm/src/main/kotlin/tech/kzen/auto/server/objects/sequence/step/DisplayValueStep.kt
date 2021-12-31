package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation
): SequenceStep<Unit> {
    override fun perform(
        activeSequenceModel: ActiveSequenceModel,
//        graphInstance: GraphInstance
    ): StepValue<Unit> {
//        val frame = imperativeModel.findLast(text)
//        val state = frame?.states?.get(text.objectPath)
//        val result = state?.previous as? ExecutionSuccess
//        val value = result?.value ?: NullExecutionValue
        println("foo: $text")
        return StepValue(
            null,
            NullExecutionValue)
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