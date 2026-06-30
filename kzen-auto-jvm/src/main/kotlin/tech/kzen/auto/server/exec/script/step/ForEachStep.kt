package tech.kzen.auto.server.exec.script.step

import tech.kzen.auto.server.exec.script.ScriptRunContext
import tech.kzen.auto.server.exec.script.ScriptStepLogic
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Loop over an iterable a predecessor produced, running [body] once per element and collecting each
 * iteration's terminal value. The iterator lives on the coroutine stack (an ordinary `for`) — no persisted
 * iterator / output / pause flag; the current element is bound under [itemBindingId] so body expressions can
 * reference it. The loop's collected value is recorded + traced by the enclosing [SequenceStep]; the body
 * drives its own steps' lifecycle.
 */
class ForEachStep(
    override val stableId: ObjectStableId,
    private val itemsStableId: ObjectStableId,
    private val itemBindingId: ObjectStableId,
    private val body: SequenceStep
): ScriptStepLogic {
    override suspend fun run(context: ScriptRunContext): TupleValue {
        val items = context.referencedValue(itemsStableId) as? Iterable<*>
            ?: error("ForEach items are not iterable: $itemsStableId")

        // Reaching here means the loop did NOT complete pre-edit (a completed loop carries its own outcome and is
        // short-circuited wholesale by the enclosing sequence). A coroutine's `for` can't be re-pointed at the
        // rebuilt body, so the loop restarts from its first iteration — drop the body's stale per-iteration
        // outcomes from the replay set so each body step executes live instead of short-circuiting on them.
        context.dropReplay(body.nestedStableIds())

        val output = ArrayList<Any?>()
        for (item in items) {
            context.record(itemBindingId, item)
            val iterationValue = body.run(context)
            output.add(iterationValue.mainComponentValue())
        }

        return TupleValue.ofMain(output)
    }


    override fun nestedStableIds(): List<ObjectStableId> {
        return listOf(stableId) + body.nestedStableIds()
    }
}
