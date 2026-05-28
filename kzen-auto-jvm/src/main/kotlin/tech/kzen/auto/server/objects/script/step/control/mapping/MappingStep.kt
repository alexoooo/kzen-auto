package tech.kzen.auto.server.objects.script.step.control.mapping

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.control.MultiStep
import tech.kzen.auto.server.service.v1.StatefulLogicElement
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.ClassNames


@Reflect
class MappingStep(
    private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation
):
    ScriptStep,
    StatefulLogicElement<MappingStep>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(MappingStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val stepsDelegate = MultiStep(steps)

    private val stepsLocationPrefix = LogicTracePath
        .ofObjectLocation(selfLocation)
        .append(ScriptConventions.stepsAttributeName.value)

    private var iterator: Iterator<*>? = null
    private var output = mutableListOf<Any>()
    private var delegatePaused: Boolean = false

    var next: Any? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadState(previous: MappingStep) {
        iterator = previous.iterator
        output = previous.output
        delegatePaused = previous.delegatePaused
        next = previous.next
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(
                TypeMetadata(
                    ClassNames.kotlinList,
                    listOf(TypeMetadata.any),
                    false))))
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        if (iterator == null) {
            val step = scriptExecutionContext.stepModel(items)

            val value = step?.value?.mainComponentValue()
            check(value is Iterable<*>) {
                "Data items expected: $items = $value"
            }
            iterator = value.iterator()
        }
        val initializedIterator = iterator!!

        while (true) {
            var wasPaused = false
            if (delegatePaused) {
                checkNotNull(next)
                delegatePaused = false
                wasPaused = true
            }
            else if (!initializedIterator.hasNext()) {
                break
            }
            else {
                next = initializedIterator.next()
            }
            checkNotNull(next)

            if (!wasPaused) {
                resetSteps(scriptExecutionContext)
            }

            val result =
                try {
                    stepsDelegate.continueOrStart(scriptExecutionContext)
                }
                catch (t: Throwable) {
                    logger.warn("Mapping error - {}", stepsDelegate, t)
                    return LogicResultFailed(ExceptionUtils.message(t))
                }

            when (result) {
                LogicResultCancelled ->
                    return result

                LogicResultPaused -> {
                    delegatePaused = true
                    return result
                }

                is LogicResultFailed ->
                    return result

                is LogicResultSuccess ->
                    output.add(result.value.mainComponentValue() ?: "<empty>")
            }

            val logicCommand = scriptExecutionContext.logicControl.pollCommand()
            if (logicCommand == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            else if (logicCommand == LogicCommand.Pause) {
                return LogicResultPaused
            }
        }

        return LogicResultSuccess(
            TupleValue.ofMain(output))
    }


    private fun resetSteps(stepContext: ScriptExecutionContext) {
        stepContext.logicTraceHandle.clearAll(stepsLocationPrefix)
        stepContext.activeScriptModel.resetAll(selfLocation, stepContext.objectStableMapper)
    }
}