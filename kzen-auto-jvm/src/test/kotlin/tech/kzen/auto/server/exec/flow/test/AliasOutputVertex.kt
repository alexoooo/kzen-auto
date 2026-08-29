package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.FlowRunOutput
import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only [FlowRunOutput]: contributes the upstream message to the run's result under `aliased-<alias>`.
 * Its archetype chains to no product signature vertex, so the harvest can only come from the capability.
 */
@Reflect
class AliasOutputVertex(
    @Suppress("unused") private val input: RequiredInput<Any?>,
    alias: String
):
    StatelessFlowVertex,
    FlowRunOutput
{
    override val bindingName = BindingName("aliased-$alias")


    override fun process() {
        throw IllegalStateException("AliasOutputVertex is harvested by FlowRun, not process()")
    }
}
