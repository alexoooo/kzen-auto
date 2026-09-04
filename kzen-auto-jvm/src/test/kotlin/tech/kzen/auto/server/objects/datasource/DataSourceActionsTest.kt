package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.FormatMaterializationActionRequest
import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.auto.common.data.format.FormatMaterializationIntent
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
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
import tech.kzen.auto.server.data.configuredTestDataPart
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith


class DataSourceActionsTest {
    companion object {
        private lateinit var moduleRoot: Path
        private lateinit var existing: Path
        private lateinit var plainExisting: Path
        private lateinit var markdownExisting: Path
        private lateinit var declaredExisting: Path
        private lateinit var missing: Path
        private lateinit var context: KzenAutoContext

        private val validSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/input")
        private val brokenSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/broken")
        private val declaredSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/declared")
        private val plainSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/plain")
        private val workerSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.workers/markdown")
        private val logicSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main.sources/logic")
        private val nonSource = ObjectLocation.parse("main/data-source-actions-test.yaml#main")


        @BeforeClass
        @JvmStatic
        fun setUp() {
            moduleRoot = Files.createTempDirectory("data-source-actions-test")
            existing = moduleRoot.resolve("existing.csv")
            plainExisting = moduleRoot.resolve("notes.txt")
            markdownExisting = moduleRoot.resolve("README.md")
            declaredExisting = moduleRoot.resolve("declared.csv")
            missing = moduleRoot.resolve("missing.csv")
            Files.writeString(existing, "name,value\nalpha,1\n")
            Files.writeString(plainExisting, "first line\nsecond line\n")
            Files.writeString(markdownExisting, "# Guide\n\n| name | value |\n| --- | --- |\n")
            Files.writeString(declaredExisting, "city,amount\nToronto,1\n")

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
                  format: main/data-source-actions-test.yaml#main.declaredFormat
                  files:
                    - location: '${declaredExisting.toString().replace('\\', '/')}'

                main.sources/plain:
                  is: FileDataSource
                  files:
                    - location: '${plainExisting.toString().replace('\\', '/')}'

                main.workers/markdown:
                  is: FileSourceWorker
                  files:
                    - location: '${markdownExisting.toString().replace('\\', '/')}'

                main.declaredFormat:
                  is: ConfiguredCsv
                  extensions:
                    - declared-csv
                  compatibleStructuredFamilies:
                    - declared-csv
                  schema: main/data-source-actions-schema.yaml#main

                main.hiddenFormat:
                  is: ConfiguredCsv
                  catalogVisible: false

                main.sources/input.authored/HiddenDelimited:
                  is: ConfiguredCsv
                  catalogVisible: false

                main.sources/input.authored/HiddenPlainText:
                  is: PlainText
                  catalogVisible: false

                main.abstractFormat:
                  abstract: true
                  is: ConfiguredCsv

                main.brokenFormat:
                  is: ConfiguredCsv
                  schema: main/missing-schema.yaml#main

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


    private fun executeFile(
        action: String,
        source: ObjectLocation,
        file: Path,
        format: String? = null,
        encoding: String? = null,
        body: ImmutableByteArray? = null
    ) = runBlocking {
        val parameters = mutableListOf(
            DataSourceConventions.sourceParameter to source.asString(),
            DataSourceConventions.actionParameter to action,
            DataSourceConventions.locationParameter to canonical(file))
        format?.let { parameters.add(DataSourceConventions.formatParameter to it) }
        encoding?.let { parameters.add(DataSourceConventions.encodingParameter to it) }
        context.detachedExecutor.execute(
            DataSourceConventions.dataSourceActionsLocation,
            ExecutionRequest(RequestParams.of(*parameters.toTypedArray()), body))
    }


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
        val resolution = result.resolutionDetails.single()
        assertEquals(canonical(existing), resolution.ref.id)
        assertEquals(FormatSelectionKind.Automatic, resolution.selection)
        assertEquals(FormatResolutionBasis.Extension, resolution.basis)
        assertTrue(resolution.concreteFormatReference.orEmpty().endsWith("#ConfiguredCsv"), resolution.toString())
        assertEquals(listOf(DataDiagnostic(
            DataDiagnostic.skipped,
            canonical(missing))), result.diagnostics)
    }


    @Test
    fun resolveFileUsesTheAuthoritativePathAndReturnsOnePart() {
        val outcome = executeFile(DataSourceConventions.resolveFileAction, validSource, existing)
        val result = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(outcome).value)

        assertEquals(1, result.manifest.units.size)
        assertEquals(1, result.manifest.units.single().parts.size)
        assertEquals(canonical(existing), result.manifest.units.single().parts.single().ref.id)
        assertEquals(1, result.resolutionDetails.size)
        assertEquals(
            result.manifest.units.single().parts.single().ref,
            result.resolutionDetails.single().ref)
    }


    @Test
    fun resolveFileUsesTheDataSourceHostedByAFileWorker() {
        val outcome = executeFile(
            DataSourceConventions.resolveFileAction, workerSource, markdownExisting)
        val result = DataResolveResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(outcome, outcome.toString()).value)

        val detail = result.resolutionDetails.single()
        assertEquals(FormatSelectionKind.Automatic, detail.selection)
        assertEquals(FormatResolutionBasis.Extension, detail.basis)
        assertEquals("Plain text", detail.displayLabel)
        assertTrue(detail.concreteFormatReference.orEmpty().endsWith("#PlainText"), detail.toString())
        assertEquals(
            canonical(markdownExisting),
            result.manifest.units.single().parts.single().ref.id)
    }


    @Test
    fun resolveFileRejectsASelectionRowThatNoLongerMatchesTheSource() {
        val outcome = executeFile(
            DataSourceConventions.resolveFileAction,
            validSource,
            existing,
            encoding = "UTF-16LE")

        val failure = assertIs<ExecutionFailure>(outcome, outcome.toString())
        assertTrue(failure.errorMessage.contains("selected file row changed"), failure.errorMessage)
    }


    @Test
    fun materializationReturnsDetachedSourceLocalNotation() {
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, validSource, existing)).value)
        val part = preview.manifest.units.single().parts.single()
        val detail = preview.resolutionDetails.single()
        val request = FormatMaterializationActionRequest(
            part,
            requireNotNull(detail.concreteFormatReference),
            mapOf("delimiter" to ";", "header" to "present", "encoding" to "UTF-8"))
        val body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray())

        val outcome = executeFile(
            DataSourceConventions.materializeFormatAction,
            validSource,
            existing,
            body = body)
        val result = FormatMaterializationActionResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(outcome, outcome.toString()).value)

        val materializedLocation = ObjectLocation.parse(result.formatReference)
        assertEquals(validSource.documentPath, materializedLocation.documentPath)
        assertTrue(materializedLocation.objectPath.startsWith(validSource.objectPath), result.formatReference)
        assertEquals(";", result.formatBody["delimiter"]?.get())
        assertEquals("present", result.formatBody["header"]?.get())
        assertEquals("UTF-8", result.encoding)
        assertEquals(null, result.schemaReference)
    }


    @Test
    fun materializationRejectsMalformedDelimitedOverrides() {
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, validSource, existing)).value)
        val request = FormatMaterializationActionRequest(
            preview.manifest.units.single().parts.single(),
            requireNotNull(preview.resolutionDetails.single().concreteFormatReference),
            mapOf("skipLeadingLines" to "many"))
        val body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray())

        val outcome = executeFile(
            DataSourceConventions.materializeFormatAction,
            validSource,
            existing,
            body = body)
        val failure = assertIs<ExecutionFailure>(outcome, outcome.toString())
        assertTrue(failure.errorMessage.contains("must be an integer"), failure.errorMessage)
    }


    @Test
    fun makeExplicitPreservesTheCanonicalReaderWithoutCreatingASchema() {
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, validSource, existing)).value)
        val request = FormatMaterializationActionRequest(
            preview.manifest.units.single().parts.single(),
            requireNotNull(preview.resolutionDetails.single().concreteFormatReference),
            emptyMap(),
            FormatMaterializationIntent.MakeExplicit)

        val outcome = executeFile(
            DataSourceConventions.materializeFormatAction,
            validSource,
            existing,
            body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray()))
        val result = FormatMaterializationActionResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(outcome, outcome.toString()).value)

        assertEquals(",", result.formatBody.values.getValue("delimiter").get())
        assertEquals("false", result.formatBody.values.getValue("catalogVisible").get())
        assertEquals(
            listOf("identity"),
            (result.formatBody.values.getValue("contentCodings")
                    as tech.kzen.lib.common.exec.ListExecutionValue).values.map { it.get() })
        assertEquals("present", result.formatBody.values.getValue("header").get())
        assertEquals("UTF-8", result.formatBody.values.getValue("charset").get())
        assertEquals(null, result.schemaReference)
        assertEquals(null, result.schemaBody)
    }


    @Test
    fun lockColumnsReturnsAnAuthoredSchemaBoundIntoTheExplicitFormat() {
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, validSource, existing)).value)
        val request = FormatMaterializationActionRequest(
            preview.manifest.units.single().parts.single(),
            requireNotNull(preview.resolutionDetails.single().concreteFormatReference),
            emptyMap(),
            FormatMaterializationIntent.LockColumns)

        val outcome = executeFile(
            DataSourceConventions.materializeFormatAction,
            validSource,
            existing,
            body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray()))
        val result = FormatMaterializationActionResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(outcome, outcome.toString()).value)

        val schemaReference = requireNotNull(result.schemaReference)
        assertEquals(schemaReference, result.formatBody.values.getValue("schema").get())
        assertEquals("false", result.formatBody.values.getValue("catalogVisible").get())
        val fields = requireNotNull(result.schemaBody).values.getValue("fields")
            as tech.kzen.lib.common.exec.MapExecutionValue
        assertEquals(listOf("name", "value"), fields.values.keys.toList())
    }


    @Test
    fun lockColumnsRejectsAReaderWhoseColumnsAreAlreadyFixed() {
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, plainSource, plainExisting)).value)
        val request = FormatMaterializationActionRequest(
            preview.manifest.units.single().parts.single(),
            requireNotNull(preview.resolutionDetails.single().concreteFormatReference),
            emptyMap(),
            FormatMaterializationIntent.LockColumns)

        val outcome = executeFile(
            DataSourceConventions.materializeFormatAction,
            plainSource,
            plainExisting,
            body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray()))

        val failure = assertIs<ExecutionFailure>(outcome, outcome.toString())
        assertTrue(failure.errorMessage.contains("does not support locking"), failure.errorMessage)
    }


    @Test
    fun materializationRejectsAStaleFingerprintBeforeReturningNotation() {
        val original = Files.readString(existing)
        val preview = DataResolveResult.ofExecutionValue(assertIs<ExecutionSuccess>(
            executeFile(DataSourceConventions.resolveFileAction, validSource, existing)).value)
        val part = preview.manifest.units.single().parts.single()
        val detail = preview.resolutionDetails.single()
        val request = FormatMaterializationActionRequest(
            part,
            requireNotNull(detail.concreteFormatReference),
            emptyMap())
        val body = ImmutableByteArray.wrap(Json.encodeToString(request).encodeToByteArray())

        try {
            Files.writeString(existing, original + "beta,22\n")
            val outcome = executeFile(
                DataSourceConventions.materializeFormatAction,
                validSource,
                existing,
                body = body)
            val failure = assertIs<ExecutionFailure>(outcome, outcome.toString())
            assertTrue(failure.errorMessage.contains("changed after preview"), failure.errorMessage)
        }
        finally {
            Files.writeString(existing, original)
        }
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

        val formatReferences = catalog.formats.map { it.reference }
        assertTrue(formatReferences.any { it.endsWith("#ConfiguredCsv") }, formatReferences.toString())
        assertTrue(formatReferences.any { it.endsWith("#ConfiguredTsv") }, formatReferences.toString())
        assertEquals(formatReferences.sorted(), formatReferences)
        assertTrue(catalog.formats.single { it.reference.endsWith("#ConfiguredCsv") }
            .extensions.contains("csv"))
        assertTrue(catalog.formats.single { it.reference.endsWith("#ConfiguredCsv") }
            .perFileOverrideAvailable)
        assertTrue(catalog.formats.single { it.reference.endsWith("#ConfiguredCsv") }
            .columnLockingAvailable)
        assertTrue(catalog.formats.single { it.reference.endsWith("#PlainText") }
            .authoringAvailable)
        assertFalse(catalog.formats.single { it.reference.endsWith("#PlainText") }
            .columnLockingAvailable)
        assertFalse(catalog.formats.single { it.reference.endsWith("#AutomaticFormat") }
            .perFileOverrideAvailable)

        // UTF-8 leads because the ordering is by how often a real file turns out to be in one, not alphabetical.
        assertEquals("UTF-8", catalog.encodings.first())
        assertTrue(catalog.encodings.size > 1)
    }


    @Test
    fun registryDiscoversConcreteFormatsExcludesUnavailableEntriesAndDiagnosesCoordinates() = runBlocking {
        val catalog = context.configuredRecordFormatRegistry.catalog()
        val references = catalog.formats.map { it.reference }

        assertTrue(references.any { it.endsWith("#ConfiguredCsv") }, references.toString())
        assertTrue(references.any { it.endsWith("#ConfiguredTsv") }, references.toString())
        assertTrue(references.any { it.endsWith("#main.declaredFormat") }, references.toString())
        assertTrue(references.none { it.endsWith("#main.hiddenFormat") }, references.toString())
        assertTrue(references.none {
            it.endsWith("#main.sources/input.authored/HiddenDelimited")
        }, references.toString())
        assertTrue(references.none {
            it.endsWith("#main.sources/input.authored/HiddenPlainText")
        }, references.toString())
        assertTrue(references.none { it.endsWith("#main.abstractFormat") }, references.toString())
        assertTrue(references.none { it.endsWith("#main.brokenFormat") }, references.toString())

        val preflight = context.configuredRecordFormatRegistry.preflight(
            "main/data-source-actions-test.yaml#main.declaredFormat")
        assertEquals("main/data-source-actions-test.yaml#main.declaredFormat", preflight.reference)
        assertEquals(FormatSelectionKind.Explicit, preflight.selectionKind)

        val hiddenDelimited =
            "main/data-source-actions-test.yaml#main.sources/input.authored/HiddenDelimited"
        val hiddenPlainText =
            "main/data-source-actions-test.yaml#main.sources/input.authored/HiddenPlainText"
        assertEquals(hiddenDelimited,
            context.configuredRecordFormatRegistry.preflight(hiddenDelimited).reference)
        assertEquals(hiddenPlainText,
            context.configuredRecordFormatRegistry.preflight(hiddenPlainText).reference)
        val candidates = context.configuredRecordFormatRegistry.candidates(FormatResolutionRequest(
            DesignDataContext(ExecutionRequest(RequestParams.of(), null)),
            DataRef(null, "orders.csv"),
            null,
            NormalizedFormatHints.of("csv"),
            null))
        assertTrue(candidates.none { it.formatReference == hiddenDelimited })
        assertTrue(candidates.none { it.formatReference == hiddenPlainText })

        val programmatic = ConfiguredDelimitedTestFormats.csv(delimiter = "^")
        assertNull(context.configuredRecordFormatRegistry.preflight(programmatic))
        assertEquals(references, context.configuredRecordFormatRegistry.catalog().formats.map { it.reference })

        val failure = assertFailsWith<IllegalArgumentException> {
            context.configuredRecordFormatRegistry.preflight(
                "main/data-source-actions-test.yaml#main.brokenFormat")
        }
        assertTrue(failure.message.orEmpty().contains("main.brokenFormat"), failure.message)
        assertTrue(failure.message.orEmpty().contains("failed to define"), failure.message)
    }


    @Test
    fun explicitShapeAlwaysInspectsResolvedContent() {
        val impossiblePart = configuredTestDataPart(
            DataRole.main, DataRef(DataSourceId("no-opener"), "opaque"), null)
        assertIs<ExecutionFailure>(executeShape(declaredSource, impossiblePart))

        val declaredResolved = DataResolveResult.ofExecutionValue(
            assertIs<ExecutionSuccess>(execute(declaredSource)).value)
        assertEquals(
            "main/data-source-actions-test.yaml#main.declaredFormat",
            declaredResolved.resolutionDetails.single().concreteFormatReference)
        val declaredOutcome = executeShape(
            declaredSource, declaredResolved.manifest.units.single().parts.single())
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
            listOf("name", "value"),
            LegacyDataShapeBridge.headerOrNull(DataShape.ofExecutionValue(inspected.value))!!
                .values.map { it.text })
    }
}
