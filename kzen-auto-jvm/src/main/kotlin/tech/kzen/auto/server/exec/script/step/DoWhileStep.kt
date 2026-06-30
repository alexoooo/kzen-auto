package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Do-while loop: run [body] once, then repeat while [condition] holds. The condition reads the body steps'
 * just-produced values (recorded in the run context during the iteration) — an ordinary `do/while` on the
 * coroutine stack, with no persisted pause flag. The body drives its own steps' trace lifecycle; the loop
 * itself yields no value.
 */
class DoWhileStep(
    override val stableId: ObjectStableId,
    private val body: SequenceStep,
    private val condition: (ScriptRunContext) -> Boolean
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        do {
            body.run(context)
        }
        while (condition(context))

        return TupleValue.empty
    }
}
