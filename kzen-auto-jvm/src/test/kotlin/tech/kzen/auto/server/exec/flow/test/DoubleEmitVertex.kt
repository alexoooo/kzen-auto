package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect


/** Test-only contract violator: sets its non-batch output twice within one execution. */
@Reflect
class DoubleEmitVertex(
    private val input: RequiredInput<Any?>,
    private val output: OptionalOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {
        output.set(input.get())
        output.set(input.get())
    }
}
