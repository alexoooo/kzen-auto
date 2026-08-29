package tech.kzen.auto.common.paradigm.flow.api

import tech.kzen.lib.common.exec.data.binding.BindingName


/**
 * Sink-vertex capability: the vertex terminates a branch and contributes to the run's result. The runner
 * captures its single upstream message as the vertex's own message and harvests that into the result bindings
 * under [bindingName], so [FlowVertex.process] is never called.
 *
 * As with [FlowRunInput], harvesting is by capability while the *declared* signature stays notation-derived.
 *
 * A vertex may combine capabilities; the runner dispatches host-first
 * ([FlowLogicHost] then [FlowRunInput] then [FlowRunOutput]).
 */
interface FlowRunOutput {
    val bindingName: BindingName
}
