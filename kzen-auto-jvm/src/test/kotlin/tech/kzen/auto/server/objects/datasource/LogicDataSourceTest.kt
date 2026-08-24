package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.FileDataOpener
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.job.worker.data.WorkerDataContext
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


class LogicDataSourceTest {
    private val instructions = ObjectLocation.parse("test/datasource/logic/dated-sales-test.yaml#main")


    @Test
    fun forwardsArgumentsInDeclaredOrderIncludingNullAndHostsExactlyOnce() {
        val unit = DataUnit.ofPath("first.csv")
        val context = RecordingContext(
            linkedMapOf("to" to "2026-01-03", "from" to "2026-01-01"),
            TupleValue.ofMain(listOf(unit)))
        val source = LogicDataSource(instructions, listOf("from", "missing", "to"))

        val result = runBlocking { source.resolve(context) }

        assertEquals(1, context.hostCount)
        assertEquals(instructions, context.hostedInstructions)
        assertEquals(listOf("from", "missing", "to"), context.hostedArguments!!.components.map { it.name.value })
        assertEquals(listOf("2026-01-01", null, "2026-01-03"), context.hostedArguments!!.components.map { it.value })
        assertEquals(listOf(unit), result.manifest.units)
        assertTrue(result.diagnostics.isEmpty())
    }


    @Test
    fun omissionsStayOmittedAndDuplicateDeclarationsAreRejected() {
        val context = RecordingContext(emptyMap(), TupleValue.ofMain(emptyList<DataUnit>()))
        val declared = mutableListOf("only")
        val source = LogicDataSource(instructions, declared)
        declared.add("added later")
        runBlocking { source.resolve(context) }
        assertEquals(listOf("only"), context.hostedArguments!!.components.map { it.name.value })

        val failure = assertFailsWith<IllegalArgumentException> {
            LogicDataSource(instructions, listOf("from", "from"))
        }
        assertTrue(failure.message!!.contains("Duplicate"))
        assertTrue(failure.message!!.contains("from"))
    }


    @Test
    fun missingOrNullMainAndEmptyIterableProduceAnEmptyManifest() {
        for (result in listOf(
            TupleValue.empty,
            TupleValue.ofMain(null),
            TupleValue.ofMain(emptyList<DataUnit>())))
        {
            val resolved = runBlocking {
                LogicDataSource(instructions, emptyList()).resolve(RecordingContext(emptyMap(), result))
            }
            assertTrue(resolved.manifest.units.isEmpty())
            assertTrue(resolved.diagnostics.isEmpty())
        }
    }


    @Test
    fun eagerIterableIsMaterializedOnceAndPreservesUnitsAndPlainReferences() {
        val units = listOf(DataUnit.ofPath("a.csv"), DataUnit.ofPath("b.csv"))
        var iteratorCount = 0
        val eager = object: Iterable<DataUnit> {
            override fun iterator(): Iterator<DataUnit> {
                iteratorCount += 1
                return units.iterator()
            }
        }

        val resolved = runBlocking {
            LogicDataSource(instructions, emptyList()).resolve(
                RecordingContext(emptyMap(), TupleValue.ofMain(eager)))
        }

        assertEquals(1, iteratorCount)
        assertEquals(units, resolved.manifest.units)
        assertTrue(resolved.manifest.units.all { it.parts.single().ref.source == null })
    }


    @Test
    fun rejectsLazySequenceIteratorScalarAndIndexedWrongElement() {
        val invalid = listOf(
            sequenceOf(DataUnit.ofPath("a.csv")),
            listOf(DataUnit.ofPath("a.csv")).iterator(),
            "not units")
        for (value in invalid) {
            val failure = assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    LogicDataSource(instructions, emptyList()).resolve(
                        RecordingContext(emptyMap(), TupleValue.ofMain(value)))
                }
            }
            assertTrue(failure.message!!.contains("eager Iterable<DataUnit>"), failure.message)
            val typeName = value::class.qualifiedName ?: value::class.simpleName ?: value.javaClass.name
            assertTrue(failure.message!!.contains(typeName), failure.message)
        }

        val wrongElement = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                LogicDataSource(instructions, emptyList()).resolve(
                    RecordingContext(
                        emptyMap(),
                        TupleValue.ofMain(listOf(DataUnit.ofPath("a.csv"), 42))))
            }
        }
        assertTrue(wrongElement.message!!.contains("element[1]"), wrongElement.message)
        assertTrue(wrongElement.message!!.contains("kotlin.Int"), wrongElement.message)
    }


    @Test
    fun declaredSchemaPublishesMainShapeOnly() {
        val schema = DataSchemaDocument(DataSchemaFieldListSpec(linkedMapOf(
            "date" to DataSchemaFieldSpec(TypeMetadata.string),
            "amount" to DataSchemaFieldSpec(TypeMetadata.int))))
        val source = LogicDataSource(instructions, emptyList(), schema)

        assertEquals(
            listOf("date", "amount"),
            assertIs<DataShape.Tabular>(source.staticShape(null)).header.values.map { it.text })
        assertEquals(source.staticShape(null), source.staticShape(DataRole.main))
        assertNull(source.staticShape(DataRole("reference")))
    }


    @Test
    fun designContextRejectsHostingWithAnActiveRunMessage() {
        val context = DesignDataContext(ExecutionRequest(RequestParams.empty, null))
        val failure = assertFailsWith<UnsupportedOperationException> {
            runBlocking { context.host(instructions, TupleValue.empty) }
        }
        assertTrue(failure.message!!.contains("requires an active run"), failure.message)
    }


    @Test
    fun workerContextDelegatesNamedTupleWithoutChangingIt() {
        val control = RecordingControl()
        val context = WorkerDataContext(control)
        val arguments = TupleValue(listOf(
            TupleComponentValue(TupleComponentName("second"), null),
            TupleComponentValue(TupleComponentName("first"), 1)))

        val returned = runBlocking { context.host(instructions, arguments) }

        assertEquals(arguments, control.arguments)
        assertEquals(TupleValue.ofMain("hosted"), returned)
    }


    @Test
    fun plainRefFromLogicAndFileSourcesUseTheSameFileOpener() {
        val file = Files.createTempFile("logic-source-opener", ".csv")
            .also { it.writeText("name,amount\nalpha,1\n") }
        val logicPart = DataUnit.ofPath(file.toString()).parts.single()
        val repository = HostReportDefinitionRepository(listOf(CsvReportDefiner()))
        val filePart = runBlocking {
            FileDataSource(
                "", "", listOf(mapOf("location" to file.toString())), "", "", "", "fail",
                FileListingAction(repository))
                .resolve(RecordingContext(emptyMap(), TupleValue.empty))
                .manifest.units.single().parts.single()
        }
        val cacheRoot = Files.createTempDirectory("logic-source-opener-cache")
        val opener = FileDataOpener(repository, SchemaCache(WorkUtils(cacheRoot)))

        assertEquals(readRows(opener, logicPart), readRows(opener, filePart))
    }


    private fun readRows(opener: FileDataOpener, part: DataPart): List<List<String>> {
        val cursor = runBlocking { opener.open(RecordingContext(emptyMap(), TupleValue.empty), part) }
        return cursor.use {
            buildList {
                while (cursor.hasNext()) {
                    add(assertIs<FlatFileRecord>(cursor.next()).toList())
                }
            }
        }
    }


    private class RecordingContext(
        private val values: Map<String, Any?>,
        private val result: TupleValue
    ): DataContext {
        var hostCount = 0
        var hostedInstructions: ObjectLocation? = null
        var hostedArguments: TupleValue? = null

        override fun argument(name: String): Any? = values[name]

        override suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
            hostCount += 1
            hostedInstructions = instructions
            hostedArguments = arguments
            return result
        }

        override suspend fun <R> blocking(block: () -> R): R = block()
    }


    private class RecordingControl: JobControl {
        var arguments: TupleValue? = null

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue = error("unused")
        override suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
            this.arguments = arguments
            return TupleValue.ofMain("hosted")
        }
    }
}
