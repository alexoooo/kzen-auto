package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.assertEquals


/**
 * Unit test for [FormulaSourceWorker] in isolation: drives the Worker's full [FormulaSourceWorker.run]
 * lifecycle (compile the Kotlin expression -> evaluate -> dispatch on the value: an Iterable streams
 * element-by-element, anything else emits once) against a capturing [ChannelOutput] and a no-op [JobControl],
 * using the real [CalculatedColumnEval] engine from a test context. Also covers the declared-parameter scope
 * (bare typed accessor, value via [JobControl.parameter]) and the live-edit stream cursor (a same-code resume
 * skips the delivered prefix; an edited expression restarts). The archetype wiring (JobChannelCreator handing
 * the Worker a real channel view) is already covered by the engine-level Job tests; this isolates the Worker's
 * own compile-and-dispatch logic.
 */
class FormulaSourceWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun evaluatesIterableExpressionAndEmitsEachElement() = runBlocking {
        val emitted = runSource("(1..5).map { \"row\" + it }")
        assertEquals(listOf("row1", "row2", "row3", "row4", "row5"), emitted)
    }


    @Test
    fun emptyIterableEmitsNothing() = runBlocking {
        val emitted = runSource("listOf<String>()")
        assertEquals(listOf(), emitted)
    }


    @Test
    fun rangeIterableEmitsEachInteger() = runBlocking {
        val emitted = runSource("1..3")
        assertEquals(listOf(1, 2, 3), emitted)
    }


    @Test
    fun scalarExpressionEmitsSingleElement() = runBlocking {
        val emitted = runSource("6 * 7")
        assertEquals(listOf(42), emitted)
    }


    @Test
    fun blankExpressionEmitsNothing() = runBlocking {
        val emitted = runSource("   ")
        assertEquals(listOf(), emitted)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun declaredParameterIsInScopeBareAndTyped() = runBlocking {
        // `number` is a bare Int accessor (declared type), so arithmetic needs no cast.
        val emitted = runSource("(1..number).toList()", parameterControl("number", 3))
        assertEquals(listOf(1, 2, 3), emitted)
    }


    @Test
    fun scalarParameterReferenceEmitsSingleElement() = runBlocking {
        val emitted = runSource("number", parameterControl("number", 7))
        assertEquals(listOf(7), emitted)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sameCodeResumeSkipsDeliveredPrefix() = runBlocking {
        val delivered = mutableListOf<Any?>()
        val first = worker("1..4", delivered)
        first.run(NoOpJobControl)
        assertEquals(listOf<Any?>(1, 2, 3, 4), delivered)

        // A rebuilt same-code instance adopts the cursor: everything was delivered, so nothing re-emits.
        val reEmitted = mutableListOf<Any?>()
        val resumed = worker("1..4", reEmitted)
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(NoOpJobControl)
        assertEquals(listOf(), reEmitted)
    }


    @Test
    fun editedCodeIgnoresCursorAndRestarts() = runBlocking {
        val first = worker("1..4")
        first.run(NoOpJobControl)

        val emitted = mutableListOf<Any?>()
        val edited = worker("1..2", emitted)
        edited.loadMigrationState(first.captureMigrationState())
        edited.run(NoOpJobControl)
        assertEquals(listOf<Any?>(1, 2), emitted)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runSource(code: String, control: JobControl = NoOpJobControl): List<Any?> {
        val emitted = mutableListOf<Any?>()
        worker(code, emitted).run(control)
        return emitted
    }


    private fun worker(code: String, emitted: MutableList<Any?> = mutableListOf()): FormulaSourceWorker {
        val output = object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                emitted.add((element as JobMessage).payload)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/formula-source-unit-test.yaml"),
            ObjectPath.parse("main.workers/source"))

        return FormulaSourceWorker(
            output, code, selfLocation, context.calculatedColumnEval)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A FormulaSourceWorker only emits + checkpoints + publishes; none of those need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A FormulaSourceWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A FormulaSourceWorker hosts no child")
    }


    // A control exposing one declared parameter [name] of the value's type (Int in these tests) bound to [value].
    private fun parameterControl(name: String, value: Int): JobControl {
        return object: JobControl by NoOpJobControl {
            override fun parameters(): TupleDefinition {
                return TupleDefinition(listOf(
                    TupleComponentDefinition(
                        TupleComponentName(name),
                        LogicType(TypeMetadata(ClassName("kotlin.Int"), listOf(), false)))))
            }

            override fun parameter(name: String): Any? = value
        }
    }
}
