package tech.kzen.auto.server.objects.script.step.control.foreach

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.control.MultiStep
import tech.kzen.lib.common.exec.logic.StatefulLogicElement
import tech.kzen.lib.common.exec.logic.model.*
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.ClassNames


@Reflect
class ForEachStep(
    private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation),
    StatefulLogicElement<ForEachStep>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ForEachStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps
    private val stepsDelegate = MultiStep(steps)

    private val stepsLocationPrefix = LogicTracePath
        .ofObjectLocation(selfLocation)
        .append(ScriptConventions.stepsAttributeName.value)

    private var iterator: Iterator<*>? = null
    private var output = mutableListOf<Any>()
    private var delegatePaused: Boolean = false

    var next: Any? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadState(previous: ForEachStep) {
        iterator = previous.iterator
        output = previous.output
        delegatePaused = previous.delegatePaused
        next = previous.next
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        // Output is a List of what the loop collects each iteration: the body's terminal step value (see
        // continueOrStart, which adds the last body step's main value to `output`), mirroring IfStep whose
        // type is its branches' terminal type. NOT the `items` element type — that's the loop variable's
        // type (ForEachItemBinding), which it reads straight from `items`. An empty body has no value to
        // collect, so its element type is unknown (Any). Defer (null) until the terminal step is validated;
        // ScriptValidator iterates to a fixpoint.
        val elementType = bodyTerminalType(scriptDefinitionContext)
            ?: return null

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(
                TypeMetadata(
                    ClassNames.kotlinList,
                    listOf(elementType),
                    false))))
    }


    // The element type the loop collects: its last body step's resolved type, Unit when that terminal
    // validated without a value, or Any for an empty body. Null means the terminal isn't validated yet —
    // the caller should defer.
    private fun bodyTerminalType(scriptDefinitionContext: ScriptDefinitionContext): TypeMetadata? {
        val terminal = bodySteps.lastOrNull()
            ?: return TypeMetadata.any

        val validation = scriptDefinitionContext.scriptValidation.stepValidations[terminal.objectPath]
            ?: return null

        return validation.typeMetadata ?: TypeMetadata.unit
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        if (iterator == null) {
            val value = scriptExecutionContext.referencedValue(items)
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

            // Surface the current iteration item in the UI (the ForEach card's detail), updated each
            // iteration. Written to this step's own trace path; the parent MultiStep's state writes
            // preserve the detail (StepTrace carries state + detail together).
            traceDetail(scriptExecutionContext, next)

            if (!wasPaused) {
                resetSteps(scriptExecutionContext)
            }

            val result =
                try {
                    stepsDelegate.continueOrStart(scriptExecutionContext)
                }
                catch (t: Throwable) {
                    logger.warn("ForEach error - {}", stepsDelegate, t)
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

            // Interruptibility between iterations. Cancel always wins. A Pause is honoured here only for
            // a degenerate empty body (no body step to stop at); for a normal body the body MultiStep's
            // step-budget gate already pauses at the next iteration's first step — so stepping advances
            // one fresh boundary without an extra "iteration complete" tick. Respect runningFreeByDepth so
            // a Step Over / Step Out of the loop runs it to completion. No budget consult here (the body
            // MultiStep owns that) — an empty body otherwise double-steps.
            val logicCommand = scriptExecutionContext.logicControl.pollCommand()
            if (logicCommand == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            else if (bodySteps.isEmpty() &&
                    logicCommand == LogicCommand.Pause &&
                    ! scriptExecutionContext.logicControl.runningFreeByDepth()
            ) {
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
