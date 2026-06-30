package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Evaluate an expression and capture it as the Script's result (last invoked ResultStep wins). Like
 * [FormulaStep] it produces its value (recorded + traced by the enclosing [SequenceStep]); the only addition
 * is [ScriptRunContext.setResult].
 */
class ResultStep(
    override val stableId: ObjectStableId,
    private val expression: (ScriptRunContext) -> Any?
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        val tuple = TupleValue.ofMain(expression(context))
        context.setResult(tuple)
        return tuple
    }
}
