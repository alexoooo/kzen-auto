package tech.kzen.auto.common.paradigm.dataflow.service.format

import tech.kzen.lib.common.exec.ExecutionValue
import kotlin.reflect.KClass


class DataflowMessageInspector {
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

        val type = message::class
        val inspector = registry[type]
                ?: throw IllegalArgumentException("Unknown ($type): $message")

        return inspector.invoke(message)
    }
}