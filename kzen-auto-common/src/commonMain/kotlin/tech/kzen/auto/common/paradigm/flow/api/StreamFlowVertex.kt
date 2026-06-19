package tech.kzen.auto.common.paradigm.flow.api


interface StreamFlowVertex<State>: FlowVertex<State> {
    /**
     * If using a StreamOutput, will be invoked to get next.
     */
    fun next(state: State): State
}