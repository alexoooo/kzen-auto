package tech.kzen.auto.common.paradigm.flow.api

import tech.kzen.lib.common.exec.ExecutionValue


/**
 * Must inject RequiredInput for use in the process method,
 *  can also inject more than one RequiredInput or additional OptionalInput.
 *
 * Can inject (at most one of) RequiredOutput or OptionalOutput.
 *
 * See: https://en.wikipedia.org/wiki/Pipe_(fluid_conveyance)
 * See: https://en.wikipedia.org/wiki/Piping_and_plumbing_fitting
 *
 * A vertex instance lives for the whole run: the runner builds the graph once per [FlowRun] and reuses
 * each vertex's instance across every execution (a live edit builds a fresh run via migration). The
 * durable per-vertex runtime is the externalized [State] threaded through [initialState] / [process],
 * NOT instance fields; the runner resets the injected channels (inputs set-or-cleared, output buffer
 * cleared) before each [process] call.
 *
 * TODO: rename to Vertex?
 * TODO: use ExecutionState<T> fields for internalized state, inline with
 */
interface FlowVertex<State> {
    /**
     * Externalized state, null for StatelessFlowVertex
     */
    fun initialState(): State


    /**
     * Non-functional view, like a structured toString.
     *
     * Called repeatedly (once per trace) and should be cheap relative to the state size — with trace
     * throttling it runs per throttle window rather than per execution, so an accumulating state does
     * not cost O(N²) serialization over a long run.
     */
    fun inspectState(state: State): ExecutionValue


    /**
     * Non-functional structured view of an emitted message — [inspectState]'s counterpart for the message
     * channel, hosted here because the emitting vertex is what knows its message types.
     *
     * Null (the default) leaves the rendering to the runner: [ExecutionValue.ofArbitrary] for basics and
     * collections of them, otherwise a truncated `toString`. Called under the same trace throttling as
     * [inspectState], so it must be cheap and must not mutate. A thrown exception is swallowed and falls back
     * to the truncated form — a trace must never fail a run the vertex itself survived.
     */
    fun inspectMessage(message: Any): ExecutionValue? {
        return null
    }


    /**
     * Make use of injected RequiredInput (and OptionalInput), plus any direct object references.
     *
     * Can also use injected RequiredOutput (or OptionalOutput).
     */
    fun process(state: State): State
}