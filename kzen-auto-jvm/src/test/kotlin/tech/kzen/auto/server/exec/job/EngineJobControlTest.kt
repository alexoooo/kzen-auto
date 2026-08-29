package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.binding.DataPresence
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class EngineJobControlTest {
    private val instructions = ObjectLocation.parse("test/child.yaml#main")
    private val signature = BindingSchema.of(listOf("first", "second", "third").map {
        BindingDefinition(
            BindingName(it),
            DataContract(DataType.Dynamic(nullable = true)),
            DataPresence.Optional)
    })


    @Test
    fun namedArgumentsRejectUnknownsButPermitOmissionsAndUseSignatureOrder() {
        val normalized = EngineJobControl.normalizeArguments(
            instructions,
            signature,
            bindings("third" to 3, "first" to 1))
        assertEquals(listOf("first", "second", "third"), normalized.entries().map { it.first.name.value })
        assertEquals(
            listOf(1, 3),
            normalized.entries().mapNotNull { (_, state) ->
                (state as? BindingState.Bound)?.value?.let(JobDataValues::boundary)
            })

        assertFailsWith<IllegalArgumentException> {
            EngineJobControl.normalizeArguments(instructions, signature, bindings("missing" to 1))
        }
    }


    @Test
    fun namedHostRemainsExplicitAndDoesNotDelegatePositionally() {
        runBlocking {
            val control = CompatibilityControl()
            val arguments = bindings("anything" to "ok")
            assertEquals(arguments, control.host(instructions = instructions, arguments = arguments))
            assertEquals(arguments, control.named)
            assertEquals(null, control.positional)
        }
    }


    private fun bindings(vararg values: Pair<String, Any?>): DataBindings {
        val schema = BindingSchema.of(values.map { (name, _) ->
            BindingDefinition(BindingName(name), DataContract(DataType.Dynamic(nullable = true)))
        })
        return DataBindings.bind(schema, values.map { (name, value) ->
            BindingName(name) to JobDataValues.lift(value)
        })
    }


    private class CompatibilityControl: JobControl {
        var positional: Any? = null
        var named: DataBindings? = null
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?): DataBindings {
            positional = input
            return DataBindings.bind(BindingSchema.empty)
        }
        override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
            named = arguments
            return arguments
        }
    }
}
