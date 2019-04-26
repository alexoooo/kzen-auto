package tech.kzen.auto.common.paradigm.dataflow.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


/**
 * Must inject RequiredInput for use in the process method,
 *  can also inject more than one RequiredInput or additional OptionalInput.
 *
 * Can inject (at most one of) RequiredOutput or OptionalOutput.
 *
 * See: https://en.wikipedia.org/wiki/Pipe_(fluid_conveyance)
 * See: https://en.wikipedia.org/wiki/Piping_and_plumbing_fitting
 */
interface Dataflow<State> {
    /**
     * Externalized state, null for StatelessDataflow
     */
    fun initialState(): State


    /**
     * Non-functional view, like a structured toString
     */
    fun inspectState(state: State): ExecutionValue


    /**
     * Make use of injected RequiredInput (and OptionalInput), plus any direct object references.
     *
     * Can also use injected RequiredOutput (or OptionalOutput).
     */
    fun process(state: State): State
}