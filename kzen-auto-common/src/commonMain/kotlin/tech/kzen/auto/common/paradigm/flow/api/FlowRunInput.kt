package tech.kzen.auto.common.paradigm.flow.api

import tech.kzen.lib.common.exec.data.binding.BindingName


/**
 * Source-vertex capability: the vertex's message is a named argument of the enclosing run rather than something
 * it computes. The runner seeds the message from the run's inputs under [bindingName] and never
 * calls [FlowVertex.process] — so an implementor's `process` is unreachable, and its declared output channel is
 * decorative (it renders an egress funnel; nothing writes it).
 *
 * Run-time seeding and the Flow's *declared* signature are separate concerns: the signature is derived from
 * notation by [FlowConventions][tech.kzen.auto.common.objects.document.flow.FlowConventions], so a vertex
 * appears in it only when its archetype chains to `FlowInput`. A capability vertex outside that chain still
 * receives whatever argument the caller passes under its [bindingName].
 *
 * A vertex may combine capabilities; the runner dispatches host-first
 * ([FlowLogicHost] then [FlowRunInput] then [FlowRunOutput]).
 */
interface FlowRunInput {
    val bindingName: BindingName
}
