package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow vertex that invokes another Logic ([instructions], e.g. a Script) as a child frame — the Flow
 * analogue of the Script paradigm's [tech.kzen.auto.server.objects.script.step.control.RunStep]. The
 * single [input] is passed as the callee's first declared parameter, and the callee's main result becomes
 * this vertex's emitted message (which downstream vertices wire to via the [output] channel).
 *
 * The invocation needs the run's [tech.kzen.lib.common.exec.logic.LogicHandle], which a [process] call
 * cannot reach, so [FlowRun][tech.kzen.auto.server.exec.flow.FlowRun] special-cases this vertex (like it
 * does [FlowInputVertex] / [FlowOutputVertex]): it starts the child execution, steps/runs it, and sets
 * the message directly. [process] is therefore never called and [output] is never written — the channel
 * is declared only so the vertex renders an egress funnel. The child participates in stepping (Step Into
 * descends into it; Step Over / Step Out / Run run it to completion).
 */
@Reflect
class RunLogicVertex(
    val instructions: ObjectLocation,
    @Suppress("unused") private val input: RequiredInput<Any?>,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {
        throw IllegalStateException("RunLogicVertex is executed by FlowRun, not process()")
    }
}
