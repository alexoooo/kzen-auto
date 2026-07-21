package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.reflect.Reflect


/** Test-only contract violator: declares a [RequiredOutput] and emits nothing. */
@Reflect
class SilentRequiredVertex(
    @Suppress("unused") private val input: RequiredInput<Any?>,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {}
}
