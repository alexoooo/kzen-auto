package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicHandleFacade
import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


data class ScriptExecutionContext(
    val logicControl: LogicControl,
    val resourceScope: LogicResourceScope,
    val activeScriptModel: ActiveScriptModel,
    val logicHandleFacade: LogicHandleFacade,
    val logicTraceHandle: LogicTraceHandle,
    val graphInstance: GraphInstance,
    // Full (live) definition this pass is running against — passed into nested RunStep executions
    // so they resolve against current notation rather than a stale start-time snapshot.
    val graphDefinition: GraphDefinition,
    val arguments: TupleValue,
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation,
    val objectStableMapper: ObjectStableMapper,
    // The Script's declared result signature (parsed once from the `results` notation). A ResultStep
    // type-checks its expression against and writes into the `main` component of this signature.
    val resultSignature: TupleDefinition
) {
    fun stepModel(objectLocation: ObjectLocation): ActiveStepModel? {
        return activeScriptModel.steps[objectStableMapper.objectStableId(objectLocation)]
    }


    // Capture a Result step's value as the Script's result (last invoked wins). Only `main` for now.
    fun setMainResult(value: Any?) {
        activeScriptModel.resultValue = TupleValue.ofMain(value)
    }

    fun resultTupleValue(): TupleValue? {
        return activeScriptModel.resultValue
    }


    /**
     * Resolve the value produced by a referenced object. For an executed body step this is its step
     * model's main value (the long-standing path). For a [ScriptValueBinding] (a parameter or loop item,
     * which is never executed) the value is resolved on demand from the binding itself — so a step can
     * reference a binding by ObjectLocation exactly like it references a prior step.
     */
    fun referencedValue(objectLocation: ObjectLocation): Any? {
        val binding = graphInstance[objectLocation]?.reference as? ScriptValueBinding
        if (binding != null) {
            return binding.resolveValue(this)
        }
        return stepModel(objectLocation)?.value?.mainComponentValue()
    }

    fun getOrPutStepModel(objectLocation: ObjectLocation): ActiveStepModel {
        return activeScriptModel.steps.getOrPut(
            objectStableMapper.objectStableId(objectLocation)
        ) { ActiveStepModel() }
    }
}