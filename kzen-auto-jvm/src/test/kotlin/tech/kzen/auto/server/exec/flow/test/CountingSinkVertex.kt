package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test-only sink [FlowVertex] for the Flow live-edit migration test
 * ([tech.kzen.auto.server.exec.flow.FlowMigrationTest]): it accumulates each received value into its state AND
 * tallies the total number of values processed across ALL instances in the static [processed] counter.
 *
 * The counter is what makes carry-vs-restart observable: a fully replayable Flow produces the SAME final output
 * whether progress carries across an edit or restarts (a restarted source simply re-emits), so only the count of
 * `process` invocations distinguishes them. With a deterministic source of N values, [processed] settles at
 * exactly N when the migration carries the per-vertex progress (each value processed once), and OVERSHOOTS N
 * when the run restarts and re-processes the already-consumed prefix.
 *
 * [note] is an otherwise-inert editable attribute: the migration test edits it to trip a realistic rebuild
 * without perturbing the source's position (mirroring the Job carryover test editing the sink's `note`).
 *
 * `@Reflect` with no KSP pass over the test source set: the graph instantiates it through the JVM reflective
 * mirror rather than a generated registration.
 */
@Reflect
class CountingSinkVertex(
    private val input: RequiredInput<Any>,
    private val note: String
):
    FlowVertex<CountingSinkVertex.State>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Total values processed across all live instances (incremented once per process()). Carried progress
        // keeps this at exactly the source total; a clean restart re-processes the prefix and overshoots it.
        val processed = AtomicInteger(0)

        fun reset() {
            processed.set(0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
        val values: MutableList<Any>
    )


    override fun initialState(): State {
        return State(mutableListOf())
    }


    override fun inspectState(state: State): ExecutionValue {
        return ExecutionValue.of(
            mapOf(
                "note" to note,
                "values" to state.values.map { it.toString() }))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process(state: State): State {
        processed.incrementAndGet()
        state.values.add(input.get())
        return state
    }
}
