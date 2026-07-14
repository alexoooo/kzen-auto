package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class ForEachStep(
    private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The loop's mid-flight migration carry ([StepExecution.recordCarry]): the LIVE [iterator] — carried as-is,
     * like any other migrating in-memory handle, so resume continues the traversal exactly where it left off
     * for ANY [Iterable] with no re-iterability constraint — plus the in-flight [currentItem] (already consumed
     * from the iterator; the resumed iteration replays it), its [currentIndex], the [collectedOutputs] so far,
     * and the [totalSize] for the progress counter. Best-effort by design (logic-spec §5): the iterator belongs
     * to the pre-edit items value, so an edit to the items PRODUCER is not reflected until the loop next starts
     * — far better for interactive development than re-running completed iterations' side effects from scratch.
     */
    private class LoopCursor(
        val iterator: Iterator<*>,
        val currentItem: Any?,
        val currentIndex: Int,
        val collectedOutputs: List<Any?>,
        val totalSize: Int?
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Loop over the iterable a predecessor produced, running the body once per element and collecting each
    // iteration's terminal value. The current element is bound under the loop's `item` binding so body
    // expressions can reference it.
    //
    // MID-LOOP MIGRATION RESUME (logic-spec §5): reaching here means the loop did NOT complete pre-edit (a
    // completed loop carries its own outcome and is short-circuited wholesale by the enclosing sequence). A
    // coroutine's loop position can't be re-pointed at the rebuilt body, so the loop re-enters from the top
    // and resumes from its restored [LoopCursor]: the live iterator continues the traversal, the in-flight
    // iteration replays with its carried item — its completed body prefix short-circuits to the frontier (its
    // outcomes are the only body entries in the restored capture; every other iteration begins with a
    // dropReplay iteration reset) — and the collected outputs are seeded. With no cursor (the loop never
    // started pre-edit) it simply runs fresh, the §5 safe default.
    override suspend fun run(execution: StepExecution): Any? {
        val itemBinding = ScriptConventions.orderedDirectChildLocations(
            execution.graphNotation, AttributeLocation(selfLocation, ScriptConventions.itemAttributePath))
            .singleOrNull()
            ?: error("ForEach step has no item binding: $selfLocation")

        val cursor = execution.restoredCarry(selfLocation) as? LoopCursor

        val iterator: Iterator<*>
        val output: ArrayList<Any?>
        val size: Int?
        var index: Int
        var item: Any?
        var replayInFlight: Boolean
        if (cursor != null) {
            iterator = cursor.iterator
            output = ArrayList(cursor.collectedOutputs)
            size = cursor.totalSize
            index = cursor.currentIndex
            item = cursor.currentItem
            replayInFlight = true
        }
        else {
            val iterable = execution.referencedValue(items) as? Iterable<*>
                ?: error("ForEach items are not iterable: $items")
            iterator = iterable.iterator()
            output = ArrayList()
            size = (iterable as? Collection<*>)?.size
            index = 0
            item = null
            replayInFlight = false
        }

        while (replayInFlight || iterator.hasNext()) {
            if (!replayInFlight) {
                item = iterator.next()

                // Iteration reset (see [StepExecution.dropReplay]) — skipped for a resumed in-flight
                // iteration, whose completed body prefix must stay replayable.
                execution.dropReplay(bodySteps)
            }

            // The cursor is (re-)recorded at each iteration's start, so a pause anywhere in the body migrates
            // with the live iterator and this iteration's item. Re-recording on the resumed run's first pass
            // matters too: its carry starts empty, so a SECOND edit must still capture the cursor.
            execution.recordCarry(selfLocation, LoopCursor(iterator, item, index, output.toList(), size))
            replayInFlight = false

            // Live loop progress on the ForEach card: the loop is the current step here, so the detail
            // attributes to it (the client renders it as "item: ..."), and markDone carries the final
            // iteration's detail into the Done trace.
            val counter = if (size != null) "${index + 1} of $size" else "${index + 1}"
            execution.traceDetail("$item ($counter)")

            execution.bind(itemBinding, item)
            output.add(execution.runSteps(bodySteps))
            index += 1
        }

        // Completed: the loop's own outcome carries; a stale cursor must not.
        execution.recordCarry(selfLocation, null)
        return output
    }


    override fun nestedStepLists(): List<List<ObjectLocation>> {
        return listOf(bodySteps)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        // Output is a List of what the loop collects each iteration: the body's terminal step value,
        // mirroring IfStep whose type is its branches' terminal type. NOT the `items` element type — that's
        // the loop variable's type (ForEachItemBinding), which it reads straight from `items`. An empty body
        // has no value to collect, so its element type is unknown (Any). Defer (null) until the terminal step
        // is validated; ScriptValidator iterates to a fixpoint.
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
}
