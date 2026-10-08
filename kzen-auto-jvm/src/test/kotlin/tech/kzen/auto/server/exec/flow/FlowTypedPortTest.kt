package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.bindingsOf
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * A vertex input typed with generics in notation (`of:`) is checked against each message when it is
 * populated: the message's run-time collection is accepted by the elements it holds, so an empty one by any
 * collection of its kind.
 */
class FlowTypedPortTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val listPortFlow = "test/flow/flow-typed-list-port-test.yaml"
    private val mapPortFlow = "test/flow/flow-typed-map-port-test.yaml"

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun listPortAcceptsAListOfStrings() {
        assertEquals(listOf("a", "b"), runFlowResult(listPortFlow, listOf("a", "b")))
    }


    @Test
    fun listPortRejectsAListOfInts() {
        assertIncompatible(listPortFlow, "FtlistPass", listOf(1, 2))
    }


    @Test
    fun listPortAcceptsAnEmptyList() {
        assertEquals(emptyList<String>(), runFlowResult(listPortFlow, ArrayList<String>()))
        assertEquals(emptyList<String>(), runFlowResult(listPortFlow, emptyList<String>()))
    }


    @Test
    fun mapPortAcceptsAMapOfIntLists() {
        assertEquals(mapOf("a" to listOf(1, 2)), runFlowResult(mapPortFlow, mapOf("a" to listOf(1, 2))))
    }


    @Test
    fun mapPortAcceptsAnEmptyMap() {
        assertEquals(emptyMap<String, List<Int>>(), runFlowResult(mapPortFlow, LinkedHashMap<String, List<Int>>()))
        assertEquals(emptyMap<String, List<Int>>(), runFlowResult(mapPortFlow, emptyMap<String, List<Int>>()))
    }


    @Test
    fun mapPortAcceptsEmptyListsBesideIntLists() {
        val argument = mapOf("a" to emptyList<Int>(), "b" to listOf(1))
        assertEquals(argument, runFlowResult(mapPortFlow, argument))
    }


    @Test
    fun mapPortRejectsAMapOfStringLists() {
        assertIncompatible(mapPortFlow, "FtmapPass", mapOf("a" to listOf("x")))
    }


    @Test
    fun mapPortRejectsStringListsBesideEmptyOnes() {
        assertIncompatible(mapPortFlow, "FtmapPass", mapOf("a" to emptyList<String>(), "b" to listOf("x")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runFlowResult(documentPathString: String, argument: Any?): Any? {
        val engine = engineFor(documentPathString, argument)
        val outcome = try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
        return JobDataValues.boundary(
            assertIs<Outcome.Success>(outcome, "$outcome").value.requireValue(BindingName("out")))
    }


    private fun assertIncompatible(documentPathString: String, vertexName: String, argument: Any?) {
        val engine = engineFor(documentPathString, argument)
        try {
            engine.pauseOnError(true)
            engine.resume()
            engine.awaitQuiescent()

            val vertexLocation = ObjectLocation(
                DocumentPath.parse(documentPathString), ObjectPath.parse(vertexName))
            val emitted = assertNotNull(engine.snapshot().root.live[
                Address.of(context.objectStableMapper.objectStableId(vertexLocation).value)])
            @Suppress("UNCHECKED_CAST")
            val vertex = VisualVertexModel.fromCollection(emitted.get() as Map<String, Any?>)
            assertTrue(
                assertNotNull(vertex.error).contains("Flow input 'input' is incompatible"),
                "vertex error: ${vertex.error}")
        }
        finally {
            engine.close()
        }
    }


    private fun engineFor(documentPathString: String, argument: Any?): RunEngine {
        context = KzenAutoContext.forTest()

        val flowLocation = ObjectLocation(DocumentPath.parse(documentPathString), ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val flowLogic = FlowLogicCompiler.compile(
            flowLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        return RunEngine(
            flowLogic,
            context.objectStableMapper.objectStableId(flowLocation),
            bindingsOf(flowLogic.signature().inputs, "x" to argument))
    }
}
