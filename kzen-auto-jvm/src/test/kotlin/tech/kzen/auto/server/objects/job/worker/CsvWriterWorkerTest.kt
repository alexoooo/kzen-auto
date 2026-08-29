package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Test
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.io.Closeable
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class CsvWriterWorkerTest {
    private lateinit var context: KzenAutoContext
    private val location = ObjectLocation(
        DocumentPath.parse("test/csv-writer-unit-test.yaml"),
        ObjectPath.parse("main.workers/write"))
    private val listing = FileListingAction(HostReportDefinitionRepository(emptyList()))


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) context.close()
    }


    @Test
    fun emptyInputCreatesParentAndYieldsFinalPlainRef() = runBlocking {
        val root = Files.createTempDirectory("csv-writer")
        try {
            val control = RecordingControl(mapOf("date" to "2026/08/24"))
            val worker = CsvWriterWorker(
                emptyInput(), root.resolve("${'$'}{date}/out.csv").toString(),
                ",", true, "file", location, listing, compiler())
            worker.run(control)

            val ref = control.yielded as DataRef
            val path = root.resolve("2026/08/24/out.csv").toAbsolutePath().normalize()
            assertEquals(path, Path.of(ref.id))
            assertEquals(null, ref.source)
            assertEquals("0", ref.attributes[DataRef.sizeKey])
            assertNotNull(ref.attributes[DataRef.modifiedKey])
            assertEquals(0L, Files.size(path))
        }
        finally {
            root.toFile().deleteRecursively()
        }
    }


    @Test
    fun complexPathParameterFailsBeforeOpeningOutput() = runBlocking {
        val root = Files.createTempDirectory("csv-writer-complex")
        try {
            val control = RecordingControl(mapOf("date" to listOf("bad")))
            assertFailsWith<IllegalArgumentException> {
                CsvWriterWorker(
                    emptyInput(), root.resolve("${'$'}{date}.csv").toString(),
                    ",", true, "file", location, listing, compiler()).run(control)
            }
            assertEquals(0L, Files.list(root).use { it.count() })
        }
        finally {
            root.toFile().deleteRecursively()
        }
    }


    @Test
    fun activeWriterRejectsAResultThatCannotAcceptDataRefBeforeOpening() = runBlocking {
        val root = Files.createTempDirectory("csv-writer-result-type")
        try {
            val path = root.resolve("wrong.csv")
            val failure = assertFailsWith<IllegalArgumentException> {
                CsvWriterWorker(
                    emptyInput(), path.toString(), ",", true, "file", location, listing, compiler())
                    .run(RecordingControl(emptyMap(), TypeMetadata.string))
            }
            assertTrue(failure.message.orEmpty().contains("writer yields DataRef"))
            assertFalse(Files.exists(path))
        }
        finally {
            root.toFile().deleteRecursively()
        }
    }


    @Test
    fun closeOwnershipSurvivesCancellationAndFailureUntilAObservedSuccess() = runBlocking {
        val cancelledClose = CountingCloseable()
        val cancelledOwner = RetriableCloseable<Closeable>()
        cancelledOwner.attach(cancelledClose)
        val cancellationControl = object: RecordingControl(emptyMap()) {
            override suspend fun <R> runBlockingIo(block: () -> R): R {
                block()
                throw CancellationException("cancelled after blocking close")
            }
        }
        assertFailsWith<CancellationException> { cancelledOwner.close(cancellationControl) }
        assertTrue(cancelledOwner.isOwned())
        assertEquals(1, cancelledClose.closeCount)
        cancelledOwner.close(null)
        assertFalse(cancelledOwner.isOwned())
        assertEquals(2, cancelledClose.closeCount)
        cancelledOwner.close(null)
        assertEquals(2, cancelledClose.closeCount, "close is idempotent after ownership clears")

        val failedClose = CountingCloseable(failures = 1)
        val failedOwner = RetriableCloseable<Closeable>()
        failedOwner.attach(failedClose)
        assertFailsWith<IllegalStateException> { failedOwner.close(null) }
        assertTrue(failedOwner.isOwned())
        failedOwner.close(null)
        assertFalse(failedOwner.isOwned())
        assertEquals(2, failedClose.closeCount)
    }


    @Test
    fun cancellationAfterBlockingCloseRetriesCleanupAndNeverYields() = runBlocking {
        val file = Files.createTempFile("csv-writer-close-cancel", ".csv")
        try {
            val control = CancellationAfterIoControl(cancelAfter = 3)
            assertFailsWith<CancellationException> {
                CsvWriterWorker(
                    emptyInput(), file.toString(), ",", true, "file", location, listing, compiler())
                    .run(control)
            }
            assertNull(control.yielded)
            Files.delete(file)
            assertFalse(Files.exists(file), "final cleanup released the writer handle")
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    @Test
    fun statFailureAfterSuccessfulCloseNeverYields() = runBlocking {
        val file = Files.createTempFile("csv-writer-stat-failure", ".csv")
        try {
            val control = FailureBeforeIoControl(failAt = 4)
            assertFailsWith<IllegalStateException> {
                CsvWriterWorker(
                    emptyInput(), file.toString(), ",", true, "file", location, listing, compiler())
                    .run(control)
            }
            assertNull(control.yielded)
        }
        finally {
            Files.deleteIfExists(file)
        }
    }


    private fun emptyInput(): ChannelInput<Any?> = object: ChannelInput<Any?> {
        override suspend fun receiveBatch(): List<Any?>? = null
        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private fun compiler() = testContext().cachedKotlinCompiler


    private fun testContext(): KzenAutoContext {
        if (!::context.isInitialized) context = KzenAutoContext.forTest()
        return context
    }


    private open class RecordingControl(
        private val parameters: Map<String, Any?>,
        private val resultType: TypeMetadata = TypeMetadata.anyNullable
    ): JobControl {
        var yielded: Any? = null

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override fun parameter(name: String): Any? = parameters[name]
        override fun results(): BindingSchema = BindingSchema.of(
            BindingDefinition(BindingName("file"), BindingSignatureDefiner.contract(resultType)))
        override fun yieldResult(component: String, value: DataValue) {
            yielded = JobDataValues.boundary(value)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }


    private class CountingCloseable(private var failures: Int = 0): Closeable {
        var closeCount = 0

        override fun close() {
            closeCount += 1
            if (failures > 0) {
                failures -= 1
                error("injected close failure")
            }
        }
    }


    private class CancellationAfterIoControl(private val cancelAfter: Int): RecordingControl(emptyMap()) {
        private var calls = 0

        override suspend fun <R> runBlockingIo(block: () -> R): R {
            calls += 1
            val result = block()
            if (calls == cancelAfter) {
                throw CancellationException("injected cancellation after IO")
            }
            return result
        }
    }


    private class FailureBeforeIoControl(private val failAt: Int): RecordingControl(emptyMap()) {
        private var calls = 0

        override suspend fun <R> runBlockingIo(block: () -> R): R {
            calls += 1
            if (calls == failAt) {
                error("injected stat failure")
            }
            return block()
        }
    }
}
