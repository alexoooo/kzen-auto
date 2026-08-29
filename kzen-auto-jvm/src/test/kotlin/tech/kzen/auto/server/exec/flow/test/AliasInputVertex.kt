package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.FlowRunInput
import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only [FlowRunInput]: seeds its message from the run argument named `aliased-<alias>` rather than from
 * the [alias] verbatim, so the run can only find the value by reading the interface's own
 * [bindingName] — no notation convention could supply it. Its [inspectMessage] override likewise pins
 * that the runner asks the vertex to render its message.
 *
 * The archetype is declared in the test notation only; the class is unknown to any product registry, so it
 * reaches the graph through the JVM reflective mirror.
 */
@Reflect
class AliasInputVertex(
    alias: String,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex,
    FlowRunInput
{
    override val bindingName = BindingName("aliased-$alias")


    override fun inspectMessage(message: Any): ExecutionValue {
        return ExecutionValue.of("inspected:$message")
    }


    override fun process() {
        throw IllegalStateException("AliasInputVertex is seeded by FlowRun, not process()")
    }
}
