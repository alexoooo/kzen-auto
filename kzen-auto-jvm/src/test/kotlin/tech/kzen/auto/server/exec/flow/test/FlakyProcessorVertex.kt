package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test-only pass-through [StatelessFlowVertex] that fails its FIRST execution (globally) and succeeds
 * thereafter — the Flow analogue of [tech.kzen.auto.server.exec.script.test.FlakyStep]. Drives
 * [tech.kzen.auto.server.exec.flow.FlowNotationTest]'s error-clears-on-resume path: the vertex parks the run
 * Suspended(Error) with the error traced (the client's red card), and a plain resume re-runs the recoverable
 * block to success — which must clear the error rather than leave the card red forever.
 *
 * Fail-once is keyed to a static counter (like the gated test Workers), so [reset] it before each test.
 *
 * `@Reflect` with no KSP pass over the test source set: the graph instantiates it through the JVM reflective
 * mirror rather than a generated registration.
 */
@Reflect
class FlakyProcessorVertex(
    private val input: RequiredInput<Any>,
    private val output: OptionalOutput<Any>
):
    StatelessFlowVertex
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val attempts = AtomicInteger(0)

        fun reset() {
            attempts.set(0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process() {
        if (attempts.getAndIncrement() == 0) {
            throw IllegalStateException("flaky failure")
        }
        output.set(input.get())
    }
}
