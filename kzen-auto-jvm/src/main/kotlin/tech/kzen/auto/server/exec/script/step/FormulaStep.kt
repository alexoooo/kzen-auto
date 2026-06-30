package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Evaluate an expression over the in-scope step values and produce its value. [expression] stands in for the
 * compiled Kotlin expression the notation-driven port supplies; the execution shape is identical. The produced
 * value is recorded + traced by the enclosing [SequenceStep] (uniform lifecycle), so the step just computes.
 */
class FormulaStep(
    override val stableId: ObjectStableId,
    private val expression: (ScriptRunContext) -> Any?
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        return TupleValue.ofMain(expression(context))
    }
}
