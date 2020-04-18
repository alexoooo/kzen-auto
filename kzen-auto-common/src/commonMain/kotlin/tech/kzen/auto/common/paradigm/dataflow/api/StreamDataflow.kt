package tech.kzen.auto.common.paradigm.dataflow.api


interface StreamDataflow<State>: Dataflow<State> {
    /**
     * If using a StreamOutput, will be invoked to get next.
     */
    fun next(state: State): State
}