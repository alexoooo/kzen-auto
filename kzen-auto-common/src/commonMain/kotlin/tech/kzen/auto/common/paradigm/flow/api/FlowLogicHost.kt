package tech.kzen.auto.common.paradigm.flow.api

import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Hosted-child capability: the vertex invokes another Logic ([instructions]) as a confined child node rather
 * than computing its message itself. The child needs the run's execution tree, which a
 * [FlowVertex.process] call cannot reach, so the runner hosts it and sets the vertex's message from the
 * callee's main result; `process` is never called and the declared output channel is decorative.
 *
 * Binding of the callee's parameters:
 * 1. the vertex's *wired* inputs (declared inputs with an upstream, in metadata order) bind positionally to
 *    the callee's leading parameters — wired input `i` to parameter `i`;
 * 2. each [arguments] entry binds its literal to the parameter it names;
 * 3. an [arguments] key naming a positionally-bound parameter, or no parameter at all, fails the compile;
 * 4. a parameter bound by neither is absent from the call, so the callee's own default applies;
 * 5. wired messages past the last parameter are dropped.
 *
 * [arguments] values are verbatim text — no coercion to a declared parameter type.
 *
 * Declare [instructions] as `is: ObjectLocation` in the vertex's metadata so live-edit migration
 * (the server's `LinkedLogicDocuments`) discovers the callee document — notation-driven, nothing to register.
 *
 * A vertex may combine capabilities; the runner dispatches host-first
 * ([FlowLogicHost] then [FlowRunInput] then [FlowRunOutput]).
 */
interface FlowLogicHost {
    val instructions: ObjectLocation

    val arguments: Map<String, String>
        get() = mapOf()
}
