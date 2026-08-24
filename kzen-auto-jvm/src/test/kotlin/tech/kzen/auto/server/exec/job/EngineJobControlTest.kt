package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class EngineJobControlTest {
    private val instructions = ObjectLocation.parse("test/child.yaml#main")
    private val signature = TupleDefinition(listOf("first", "second", "third").map {
        TupleComponentDefinition(TupleComponentName(it), LogicType(TypeMetadata.anyNullable))
    })


    @Test
    fun namedArgumentsRejectDuplicatesAndUnknownsButPermitOmissionsAndUseSignatureOrder() {
        val normalized = EngineJobControl.normalizeArguments(
            instructions,
            signature,
            tuple("third" to 3, "first" to 1))
        assertEquals(listOf("first", "third"), normalized.components.map { it.name.value })
        assertEquals(listOf(1, 3), normalized.components.map { it.value })

        assertFailsWith<IllegalArgumentException> {
            EngineJobControl.normalizeArguments(
                instructions, signature,
                TupleValue(listOf(
                    TupleComponentValue(TupleComponentName("first"), 1),
                    TupleComponentValue(TupleComponentName("first"), 2))))
        }
        assertFailsWith<IllegalArgumentException> {
            EngineJobControl.normalizeArguments(instructions, signature, tuple("missing" to 1))
        }
    }


    @Test
    fun compatibilityNamedHostDelegatesExactlyOneComponentOnly() {
        runBlocking {
            val control = CompatibilityControl()
            assertEquals(
                TupleValue.ofMain("ok"),
                control.host(instructions = instructions, arguments = tuple("anything" to "ok")))
            assertEquals("ok", control.positional)

            assertFailsWith<IllegalStateException> {
                control.host(instructions = instructions, arguments = TupleValue.empty)
            }
            assertFailsWith<IllegalStateException> {
                control.host(instructions = instructions, arguments = tuple("a" to 1, "b" to 2))
            }
        }
    }


    private fun tuple(vararg values: Pair<String, Any?>): TupleValue = TupleValue(values.map {
        TupleComponentValue(TupleComponentName(it.first), it.second)
    })


    private class CompatibilityControl: JobControl {
        var positional: Any? = null
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue {
            positional = input
            return TupleValue.ofMain(input)
        }
    }
}
