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
        // Reaching here means the loop did NOT complete pre-edit; a coroutine's do/while can't be re-pointed at
        // the rebuilt body, so it restarts from the first iteration — drop the body's stale per-iteration
        // outcomes from the replay set so each body step executes live (see ForEachStep for the full rationale).
        context.dropReplay(body.nestedStableIds())

        do {
            body.run(context)
        }
        while (condition(context))

        return TupleValue.empty
    }


    override fun nestedStableIds(): List<ObjectStableId> {
        return listOf(stableId) + body.nestedStableIds()
    }
}
