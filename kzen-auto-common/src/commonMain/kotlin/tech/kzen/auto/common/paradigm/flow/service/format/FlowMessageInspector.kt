package tech.kzen.auto.common.paradigm.flow.service.format

import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.lib.common.exec.ExecutionValue
import kotlin.reflect.KClass


/**
 * Renders a Flow vertex's message value as an [ExecutionValue] for tracing.
 *
 * The registry is optional: a message that [ExecutionValue.ofArbitrary] already understands
 * (basics + lists/maps of them) is rendered directly; otherwise a registered inspector whose type
 * the message is an instance of is used (supertype-aware, so registering for an interface works);
 * failing that the message renders via a truncated [toString]. It never throws — a trace must not
 * be able to fail a run the vertex itself survived.
 */
class FlowMessageInspector {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun truncatedToString(value: Any): ExecutionValue {
            return ExecutionValue.of(
                TraceDisplay.truncatedToString(value, TraceDisplay.maxFlowTraceChars))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val registry = mutableMapOf<KClass<*>, (Any) -> ExecutionValue>()


    fun <T: Any> register(type: KClass<T>, inspector: (T) -> ExecutionValue) {
        check(type !in registry) {
            "Duplicate registry: $type"
        }

        @Suppress("UNCHECKED_CAST")
        registry[type] = inspector as (Any) -> ExecutionValue
    }


    fun inspectMessage(message: Any): ExecutionValue {
        val asBasic = ExecutionValue.ofArbitrary(message)
        if (asBasic != null) {
            return asBasic
        }

        val inspector = registry.entries.firstOrNull { it.key.isInstance(message) }?.value
        if (inspector != null) {
            return inspector.invoke(message)
        }

        return truncatedToString(message)
    }
}
