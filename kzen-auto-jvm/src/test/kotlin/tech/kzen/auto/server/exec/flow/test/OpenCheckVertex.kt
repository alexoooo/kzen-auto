package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only vertex that fails unless its input is a still-open [CloseCountingResource], emitting its name:
 * downstream of a [CloseableSourceVertex] it proves the owned message reached it live, and what it passes on
 * is a plain value the run's output can be read through after the Flow has settled and closed the resource.
 */
@Reflect
class OpenCheckVertex(
    private val input: RequiredInput<Any?>,
    private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {
        val value = input.get()
        check(value is CloseCountingResource) { "Not a CloseCountingResource: $value" }
        check(!value.isClosed) { "Already closed: $value" }
        output.set(value.name)
    }
}
