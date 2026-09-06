package tech.kzen.auto.server.objects.job.worker.javafixture

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * The Java-friendly adapters exercised from javac-compiled fixtures through a real Job: a cursor source, a
 * transform with per-element and completion callbacks, and a blocking reader — no Continuation, no Emitter,
 * no Kotlin source on the plugin side.
 */
class JavaAdaptersTest {
    private val documentPath = DocumentPath.parse("test/job/plugin/java-adapters-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val latchTimeoutSeconds = 10L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        JavaCountingSource.reset()
        CollectingSinkWorker.reset()
    }

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun javaSourceAndTransformRunEndToEndAndCloseTheirResources() {
        JavaCountingSource.count = 3
        val outcome = run()
        assertIs<Outcome.Success>(outcome)

        assertEquals(listOf<Any?>(0, 0, 1, 2, 2, 4, "total=3"), CollectingSinkWorker.received.toList())
        assertEquals(1, JavaCountingSource.opened.get())
        assertEquals(1, JavaCountingSource.cursorsClosed.get(), "the closeable cursor is closed once")
        assertEquals(3, JavaCountingSource.itemsCreated.get())
    }


    @Test
    fun declaredOutputClassLiftsRecordsUnderTheirOwnShape() {
        JavaCountingSource.count = 2
        val outcome = run(DocumentPath.parse("test/job/plugin/java-record-shape-test.yaml"))
        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf("Summary[value=0, label=even]", "Summary[value=1, label=odd]"),
            CollectingSinkWorker.received.map { it.toString() })
    }


    @Test
    fun failedOpenIsTheRunsFailureAndLeavesNoCursor() {
        JavaCountingSource.failOpen = true
        val outcome = run()
        assertIs<Outcome.Failed>(outcome)
        assertEquals(0, JavaCountingSource.opened.get())
        assertEquals(0, JavaCountingSource.cursorsClosed.get())
    }


    @Test
    fun cancellationAfterAcquisitionBeforeDeliveryClosesTheAcquiredItem() {
        val pulled = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        JavaCountingSource.pulled = pulled
        JavaCountingSource.proceed = proceed

        val engine = newEngine()
        val outcome = try {
            runBlocking {
                engine.resume()
                assertTrue(pulled.await(latchTimeoutSeconds, TimeUnit.SECONDS), "the first pull acquired its item")
                // The item exists inside the blocking body; cancel before the body returns to the coroutine
                engine.cancel()
                proceed.countDown()
                engine.await()
            }
        }
        finally {
            engine.close()
        }

        assertIs<Outcome.Cancelled>(outcome)
        assertEquals(1, JavaCountingSource.itemsCreated.get())
        assertEquals(1, JavaCountingSource.itemsClosed.get(), "the acquired but undelivered item is closed")
        assertEquals(1, JavaCountingSource.cursorsClosed.get())
        assertTrue(CollectingSinkWorker.received.isEmpty())
    }


    @Test
    fun blockingReaderServesTheSuspendContractFromJava() {
        val reader = JavaBlockingReader()
        val bytes = "alpha\nbeta\ngamma".toByteArray()
        val input = object: ReaderByteInput {
            private var position = 0
            override val expandedBytesRead: Long get() = position.toLong()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= bytes.size) return -1
                val n = minOf(length, bytes.size - position)
                System.arraycopy(bytes, position, buffer, offset, n)
                position += n
                return n
            }
        }
        val request = ReaderOpenRequest("fixture", null, reader.decode(MapExecutionValue(mapOf())), input, ReadOperationalPolicy())
        val lines = runBlocking {
            val cursor = reader.open(request)
            val read = mutableListOf<Any?>()
            cursor.use {
                while (it.hasNext()) {
                    val value = it.next()
                    read.add(value.access.readText(value.root))
                }
            }
            read
        }
        assertEquals(3, lines.size)

        val shape = runBlocking { reader.inspect(ReaderInspectionRequest(request, 1)) }
        assertTrue(shape.itemType.toString().isNotEmpty())
        assertEquals("java.fixture", reader.identity.namespace)
        assertEquals(MapExecutionValue(mapOf()), reader.encode(reader.canonicalize(reader.decode(MapExecutionValue(mapOf())))))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun run(document: DocumentPath = documentPath): Outcome {
        val engine = newEngine(document)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    private fun newEngine(document: DocumentPath = documentPath): RunEngine {
        val jobLocation = ObjectLocation(document, ObjectPath.parse("main"))
        context = KzenAutoContext.forTest()
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val jobLogic = JobLogicCompiler.compile(
            jobLocation,
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
        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
    }
}
