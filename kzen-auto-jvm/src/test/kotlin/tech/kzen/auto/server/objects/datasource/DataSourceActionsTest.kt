package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.model.DataDiagnostic
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.util.ImmutableByteArray
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


class DataSourceActionsTest {
    companion object {
        private lateinit var moduleRoot: Path
        private lateinit var existing: Path
        private lateinit var missing: Path
        private lateinit var context: KzenAutoContext

        private val validSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/input")
        private val brokenSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/broken")
        private val declaredSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/declared")
        private val logicSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/logic")
        private val nonSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main")


        @BeforeClass
        @JvmStatic
        fun setUp() {
            moduleRoot = Files.createTempDirectory("data-source-actions-test")
            existing = moduleRoot.resolve("existing.csv")
            missing = moduleRoot.resolve("missing.csv")
            Files.writeString(existing, "name\nvalue\n")

            val notationDir = moduleRoot.resolve("src/main/resources/notation/main")
            Files.createDirectories(notationDir)
            Files.writeString(notationDir.resolve("data-source-actions-test.yaml"), """
                main:
                  is: Job

                main.sources/input:
                  is: FileDataSource
                  files:
                    - location: '${existing.toString().replace('\\', '/')}'
                    - location: '${missing.toString().replace('\\', '/')}'
                  missing: skip

                main.sources/broken:
                  is: FileDataSource
                  files:
                    - location: '${missing.toString().replace('\\', '/')}'

                main.sources/declared:
                  is: FileDataSource
                  schema: main/data-source-actions-schema.yaml#main

                main.sources/logic:
                  is: LogicDataSource
                  instructions: main/data-source-actions-test.yaml#main
            """.trimIndent())
            Files.writeString(notationDir.resolve("data-source-actions-schema.yaml"), """
                main:
                  is: DataSchema
                  fields:
                    city:
                      class: kotlin.String
                      of: []
                      nullable: false
                    amount:
                      class: kotlin.Int
                      of: []
                      nullable: false
            """.trimIndent())

            context = KzenAutoContext.create(KzenAutoConfig(
                jsModuleName = "kzen-auto-js",
                moduleRoot = moduleRoot))
        }


        @AfterClass
        @JvmStatic
        fun tearDown() {
            context.close()
            WorkUtils.recursivelyDeleteDir(moduleRoot)
        }
    }


    private fun execute(source: ObjectLocation, action: String = DataSourceConventions.resolveAction) =
        execute(source.asString(), action)

    private fun execute(source: String, action: String = DataSourceConventions.resolveAction) =
        runBlocking {
            context.detachedExecutor.execute(
                DataSourceConventions.dataSourceActionsLocation,
                ExecutionRequest(RequestParams.of(
                    DataSourceConventions.sourceParameter to source,
                    DataSourceConventions.actionParameter to action), null))
        }

    private fun canonical(path: Path): String = DataLocation.of(path.toAbsolutePath().normalize().toString()).asString()


    private fun executeShape(source: ObjectLocation, part: DataPart) = runBlocking {
        val body = ImmutableByteArray.wrap(Json.encodeToString(part).encodeToByteArray())
        context.detachedExecutor.execute(
            DataSourceConventions.dataSourceActionsLocation,
            ExecutionRequest(
                RequestParams.of(
                    DataSourceConventions.sourceParameter to source.asString(),
                    DataSourceConventions.actionParameter to DataSourceConventions.shapeAction),
                body))
    }


    @Test
    fun resolveLowersManifestAndDiagnosticsThroughDetachedExecutor() {
        val outcome = execute(validSource)
        val success = assertIs<ExecutionSuccess>(outcome, outcome.toString())
        val result = DataResolveResult.ofExecutionValue(success.value)

        assertEquals(1, result.manifest.units.size)
        assertEquals(canonical(existing),
            result.manifest.units.single().parts.single().ref.id)
        assertEquals(listOf(DataDiagnostic(
            DataDiagnostic.skipped,
            canonical(missing))), result.diagnostics)
    }


    @Test
    fun nonSourceAndUnknownActionAreExecutionFailures() {
        val nonSourceFailure = assertIs<ExecutionFailure>(execute(nonSource))
        assertTrue(nonSourceFailure.errorMessage.contains("Not a DataSource"))
        assertTrue(nonSourceFailure.errorMessage.contains(nonSource.asString()))

        val unknownAction = assertIs<ExecutionFailure>(execute(validSource, "unknown"))
        assertTrue(unknownAction.errorMessage.contains("Unknown data source action"))
        assertTrue(unknownAction.errorMessage.contains("unknown"))

        val malformedSource = assertIs<ExecutionFailure>(execute("bad#too#many"))
        assertTrue(malformedSource.errorMessage.isNotBlank())
    }


    @Test
    fun resolverExceptionIsNormalizedToExecutionFailure() {
        val failure = assertIs<ExecutionFailure>(execute(brokenSource))
        assertTrue(failure.errorMessage.contains("Missing file"), failure.errorMessage)
        assertTrue(failure.errorMessage.contains(missing.fileName.toString()))
    }


    @Test
    fun logicResolutionExplainsThatAnActiveRunIsRequired() {
        val failure = assertIs<ExecutionFailure>(execute(logicSource))
        assertTrue(failure.errorMessage.contains("requires an active run"), failure.errorMessage)
        assertTrue(failure.errorMessage.contains("runs its logic"), failure.errorMessage)
    }


    // The Format / Encoding selects ask for this before anything is configured — including on a source that
    // cannot be instantiated — so it must answer with no `source` parameter at all.
    @Test
    fun fileFormatsAnswersWithoutASourceAndListsWhatTheReaderCouldUse() {
        val outcome = runBlocking {
            context.detachedExecutor.execute(
                DataSourceConventions.dataSourceActionsLocation,
                ExecutionRequest(RequestParams.of(
                    DataSourceConventions.actionParameter to DataSourceConventions.fileFormatsAction), null))
        }

        val success = assertIs<ExecutionSuccess>(outcome, outcome.toString())

        @Suppress("UNCHECKED_CAST")
        val catalog = FileFormatCatalog.ofCollection(success.value.get() as Map<String, Any?>)

        val formatNames = catalog.formats.map { it.coordinate.asString() }
        assertTrue(formatNames.containsAll(listOf("CSV", "TSV", "Text")), formatNames.toString())
        assertEquals(formatNames.sorted(), formatNames)
        assertTrue(catalog.formats.single { it.coordinate.asString() == "CSV" }.extensions.contains("csv"))

        // UTF-8 leads because the ordering is by how often a real file turns out to be in one, not alphabetical.
        assertEquals("UTF-8", catalog.encodings.first())
        assertTrue(catalog.encodings.size > 1)
    }


    @Test
    fun declaredShapeWinsBeforeOpenerLookupAndUndeclaredFallsBack() {
        val impossiblePart = DataPart(
            DataRole.main, DataRef(DataSourceId("no-opener"), "opaque"), null, null)
        val declaredOutcome = executeShape(declaredSource, impossiblePart)
        val declared = assertIs<ExecutionSuccess>(declaredOutcome, declaredOutcome.toString())
        assertEquals(
            listOf("city", "amount"),
            LegacyDataShapeBridge.headerOrNull(DataShape.ofExecutionValue(declared.value))!!
                .values.map { it.text })

        val resolved = DataResolveResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(execute(validSource)).value)
        val inspected = assertIs<ExecutionSuccess>(
            executeShape(validSource, resolved.manifest.units.single().parts.single()))
        assertEquals(
            listOf("name"),
            LegacyDataShapeBridge.headerOrNull(DataShape.ofExecutionValue(inspected.value))!!
                .values.map { it.text })
    }
}
