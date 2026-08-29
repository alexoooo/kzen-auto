package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.FileDataOpener
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.job.worker.data.WorkerDataContext
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
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
            result(listOf(unit)))
        val source = LogicDataSource(instructions, listOf("from", "missing", "to"))

        val result = runBlocking { source.resolve(context) }

        assertEquals(1, context.hostCount)
        assertEquals(instructions, context.hostedInstructions)
        assertEquals(listOf("from", "missing", "to"), context.hostedArguments!!.entries().map { it.first.name.value })
        assertEquals(
            listOf("2026-01-01", null, "2026-01-03"),
            context.hostedArguments!!.entries().map { (_, state) ->
                JobDataValues.boundary(assertIs<BindingState.Bound>(state).value)
            })
        assertEquals(listOf(unit), result.manifest.units)
        assertTrue(result.diagnostics.isEmpty())
    }


    @Test
    fun omissionsStayOmittedAndDuplicateDeclarationsAreRejected() {
        val context = RecordingContext(emptyMap(), result(emptyList<DataUnit>()))
        val declared = mutableListOf("only")
        val source = LogicDataSource(instructions, declared)
        declared.add("added later")
        runBlocking { source.resolve(context) }
        assertEquals(listOf("only"), context.hostedArguments!!.entries().map { it.first.name.value })

        val failure = assertFailsWith<IllegalArgumentException> {
            LogicDataSource(instructions, listOf("from", "from"))
        }
        assertTrue(failure.message!!.contains("Duplicate"))
        assertTrue(failure.message!!.contains("from"))
    }


    @Test
    fun missingOrNullMainAndEmptyIterableProduceAnEmptyManifest() {
        for (result in listOf(
            DataBindings.bind(BindingSchema.empty),
            result(null),
            result(emptyList<DataUnit>())))
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
                RecordingContext(emptyMap(), result(eager)))
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
                        RecordingContext(emptyMap(), result(value)))
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
                        result(listOf(DataUnit.ofPath("a.csv"), 42))))
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
            LegacyDataShapeBridge.headerOrNull(source.staticShape(null)!!)!!.values.map { it.text })
        assertEquals(source.staticShape(null), source.staticShape(DataRole.main))
        assertNull(source.staticShape(DataRole("reference")))
    }


    @Test
    fun designContextRejectsHostingWithAnActiveRunMessage() {
        val context = DesignDataContext(ExecutionRequest(RequestParams.empty, null))
        val failure = assertFailsWith<UnsupportedOperationException> {
            runBlocking { context.host(instructions, DataBindings.bind(BindingSchema.empty)) }
        }
        assertTrue(failure.message!!.contains("requires an active run"), failure.message)
    }


    @Test
    fun designContextPreservesRequestCardinalityAndRemovesRoutingParameters() {
        val context = DesignDataContext(ExecutionRequest(RequestParams(mapOf(
            "single" to listOf("one"),
            "repeated" to listOf("one", "two"),
            DataSourceConventions.actionParameter to listOf("resolve"),
            DataSourceConventions.sourceParameter to listOf("source-id"))), null))

        assertNull(context.argument("missing"))
        assertEquals("one", context.argument("single"))
        assertEquals(listOf("one", "two"), context.argument("repeated"))
        assertNull(context.argument(DataSourceConventions.actionParameter))
        assertNull(context.argument(DataSourceConventions.sourceParameter))
    }


    @Test
    fun workerContextDelegatesNamedBindingsWithoutChangingThem() {
        val control = RecordingControl()
        val context = WorkerDataContext(control)
        val arguments = arguments("second" to null, "first" to 1)

        val returned = runBlocking { context.host(instructions, arguments) }

        assertEquals(arguments, control.arguments)
        assertEquals("hosted", JobDataValues.boundary(returned.requireValue(BindingName("main"))))
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
                .resolve(RecordingContext(emptyMap(), DataBindings.bind(BindingSchema.empty)))
                .manifest.units.single().parts.single()
        }
        val cacheRoot = Files.createTempDirectory("logic-source-opener-cache")
        val opener = FileDataOpener(repository, SchemaCache(WorkUtils(cacheRoot)))

        assertEquals(readRows(opener, logicPart), readRows(opener, filePart))
    }


    private fun readRows(opener: FileDataOpener, part: DataPart): List<List<String>> {
        val cursor = runBlocking {
            opener.open(RecordingContext(emptyMap(), DataBindings.bind(BindingSchema.empty)), part)
        }
        return cursor.use {
            buildList {
                while (cursor.hasNext()) {
                    add(assertIs<FlatFileRecord>(cursor.next().access).toList())
                }
            }
        }
    }


    private class RecordingContext(
        private val values: Map<String, Any?>,
        private val result: DataBindings
    ): DataContext {
        var hostCount = 0
        var hostedInstructions: ObjectLocation? = null
        var hostedArguments: DataBindings? = null

        override fun argument(name: String): Any? = values[name]

        override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
            hostCount += 1
            hostedInstructions = instructions
            hostedArguments = arguments
            return result
        }

        override suspend fun <R> blocking(block: () -> R): R = block()
    }


    private inner class RecordingControl: JobControl {
        var arguments: DataBindings? = null

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?): DataBindings = error("unused")
        override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
            this.arguments = arguments
            return result("hosted")
        }
    }


    private fun result(value: Any?): DataBindings {
        val data = LiteralDataValues.lift(value)
        val contract = DataContract(DataType.Dynamic(nullable = true))
        val schema = BindingSchema.of(BindingDefinition(BindingName("main"), contract))
        return DataBindings.bind(
            schema,
            BindingName("main") to data)
    }


    private fun arguments(vararg values: Pair<String, Any?>): DataBindings {
        val schema = BindingSchema.of(values.map { (name, _) ->
            BindingDefinition(BindingName(name), DataContract(DataType.Dynamic(nullable = true)))
        })
        return DataBindings.bind(schema, values.map { (name, value) ->
            BindingName(name) to JobDataValues.lift(value)
        })
    }
}
