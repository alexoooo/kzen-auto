package tech.kzen.auto.server.objects.script.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpression
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionCompiler
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.StatefulLogicElement
import tech.kzen.lib.common.exec.logic.model.*
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.ClassNames


/**
 * Runs its body branch, then evaluates a user-supplied Kotlin [condition] (compiled like [FormulaStep]'s
 * formula, but *required* to be Boolean — so no type inference); while the condition is true the body
 * repeats. The condition can reference the body steps' just-produced values by name (plus any in-scope
 * parameters / enclosing loop items). Loop semantics are do-while: the body always runs at least once and
 * the condition is checked after each pass.
 *
 * The loop machinery mirrors [tech.kzen.auto.server.objects.script.step.control.foreach.ForEachStep] minus
 * the iterator (pause/resume via [delegatePaused], a fresh per-iteration trace via [resetSteps], and a
 * Cancel/Pause poll each pass so a runaway loop stays interruptible).
 */
@Reflect
class DoWhileStep(
    private val condition: String,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep,
    StatefulLogicElement<DoWhileStep>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DoWhileStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps
    private val stepsDelegate = MultiStep(steps)

    private val stepsLocationPrefix = LogicTracePath
        .ofObjectLocation(selfLocation)
        .append(ScriptConventions.stepsAttributeName.value)

    private var delegatePaused: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadState(previous: DoWhileStep) {
        delegatePaused = previous.delegatePaused
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val scopeNullable = conditionScopeTypes(
            scriptDefinitionContext.scriptTree, scriptDefinitionContext.scriptValidation)

        // Defer (null) until every in-scope value's type is known — like ForEachStep defers on `items`.
        // The ScriptValidator iterates to a fixpoint, so a later pass resolves the body step types.
        if (scopeNullable.values.any { it == null }) {
            return null
        }

        val code = generateConditionCode(nonUnitScope(scopeNullable))
        val error = cachedKotlinCompiler.tryCompile(code, ClassLoaderUtils.dynamicParentClassLoader())
        if (error != null) {
            return ScriptStepDefinition(null, error)
        }

        return ScriptStepDefinition.of(TupleDefinition.empty)
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        while (true) {
            var wasPaused = false
            if (delegatePaused) {
                delegatePaused = false
                wasPaused = true
            }

            // Fresh trace + step state for a new iteration; on resume keep the partially-run body.
            if (! wasPaused) {
                resetSteps(scriptExecutionContext)
            }

            val result =
                try {
                    stepsDelegate.continueOrStart(scriptExecutionContext)
                }
                catch (t: Throwable) {
                    logger.warn("Do-while body error - {}", stepsDelegate, t)
                    return LogicResultFailed(ExceptionUtils.message(t))
                }

            when (result) {
                LogicResultCancelled ->
                    return result

                is LogicResultPaused -> {
                    delegatePaused = true
                    return result
                }

                is LogicResultFailed ->
                    return result

                is LogicResultSuccess -> {
                    // Body iteration complete — evaluate the condition below.
                }
            }

            val keepGoing =
                try {
                    evaluateCondition(scriptExecutionContext)
                }
                catch (t: Throwable) {
                    logger.warn("Do-while condition error - {}", selfLocation, t)
                    return LogicResultFailed(ExceptionUtils.message(t))
                }

            if (! keepGoing) {
                break
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
                return LogicResultPaused()
            }
        }

        return LogicResultSuccess(TupleValue.empty)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun evaluateCondition(scriptExecutionContext: ScriptExecutionContext): Boolean {
        val scopeNullable = conditionScopeTypes(
            scriptExecutionContext.scriptTree, scriptExecutionContext.scriptValidation)

        val missing = scopeNullable.filterValues { it == null }.keys
        check(missing.isEmpty()) {
            "Can't determine type: $missing"
        }

        val scope = nonUnitScope(scopeNullable)
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val code = generateConditionCode(scope)

        val error = cachedKotlinCompiler.tryCompile(code, classLoader)
        check(error == null) {
            "Unable to compile condition: $error - ${code.sourceText}"
        }

        val clazz = cachedKotlinCompiler.tryLoad(code, classLoader)
        check(clazz != null) {
            "Unable to load condition: ${code.sourceText}"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<StepExpression>
        val instance = classCast.getDeclaredConstructor().newInstance()

        // Same scope iteration order as the generated accessors, so values line up by index.
        val values = scope.keys.map {
            scriptExecutionContext.referencedValue(selfLocation.documentPath.toObjectLocation(it))
        }

        val value = instance.evaluate(values)
        check(value is Boolean) {
            "Do-while condition must be Boolean: $selfLocation = $value"
        }
        return value
    }


    private fun generateConditionCode(scope: Map<ObjectPath, TypeMetadata>) =
        StepExpressionCompiler.generateCode(
            "Cond_" + StepExpressionCompiler.sanitizeName(selfLocation.objectPath.name.value),
            "Boolean",
            condition,
            scope)


    /**
     * The values the condition can reference by name: the body steps (this loop's children) plus the
     * parameters / enclosing loop items in scope. Body steps are children — not predecessors — so they're
     * taken from the constructor's `steps` list, not [ScriptTree.predecessors].
     */
    private fun conditionScopeTypes(
        scriptTree: ScriptTree,
        scriptValidation: ScriptValidation
    ): Map<ObjectPath, TypeMetadata?> {
        val builder = LinkedHashMap<ObjectPath, TypeMetadata?>()

        val bindings = scriptTree.inScopeBindingPaths(selfLocation.objectPath)
        val bodyPaths = bodySteps.map { it.objectPath }

        for (path in bodyPaths + bindings) {
            builder[path] = scriptValidation.stepValidations[path]?.typeMetadata
        }

        return builder
    }


    // Drop Unit-typed values (e.g. WaitStep) — they carry no usable value and only the named, typed
    // results are addressable from the condition.
    @Suppress("UNCHECKED_CAST")
    private fun nonUnitScope(scopeNullable: Map<ObjectPath, TypeMetadata?>): Map<ObjectPath, TypeMetadata> {
        return (scopeNullable as Map<ObjectPath, TypeMetadata>)
            .filterValues { it.className != ClassNames.kotlinUnit }
    }


    private fun resetSteps(scriptExecutionContext: ScriptExecutionContext) {
        scriptExecutionContext.logicTraceHandle.clearAll(stepsLocationPrefix)
        scriptExecutionContext.activeScriptModel.resetAll(
            selfLocation, scriptExecutionContext.objectStableMapper)
    }
}
