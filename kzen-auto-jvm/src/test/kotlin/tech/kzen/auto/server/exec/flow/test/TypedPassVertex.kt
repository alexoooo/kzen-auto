package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.reflect.Reflect


/** Test-only pass-through whose archetypes declare the input's payload type in notation (`of:`). */
@Reflect
class TypedPassVertex(
    private val input: RequiredInput<Any?>,
    private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {
        output.set(input.get())
    }
}
