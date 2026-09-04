package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataDiagnostic
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatPreflight
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind


class FileDataSourceTest {
    private object DirectContext: DataContext {
        override fun argument(name: String): Any? = null
        override suspend fun <R> blocking(block: () -> R): R = block()
    }

    private val listing = FileListingAction(HostReportDefinitionRepository(emptyList()))

    private fun source(
        directory: String = "",
        filter: String = "",
        files: List<Map<String, String>> = emptyList(),
        format: ConfiguredRecordFormat? = null,
        groupPattern: String = "",
        missing: String = FileDataSource.missingFail,
        schema: DataSchemaDocument? = null,
        formatLookup: ConfiguredRecordFormatLookup = missingFormatLookup
    ): FileDataSource = FileDataSource(
        directory, filter, files, format ?: ConfiguredDelimitedTestFormats.csv(schema),
        groupPattern, missing, listing, formatLookup)

    private fun resolve(source: FileDataSource) = runBlocking { source.resolve(DirectContext) }

    private fun canonical(path: Path): String = DataLocation.of(path.toAbsolutePath().normalize().toString()).asString()

    private fun picked(path: Path, format: String? = null, encoding: String? = null): Map<String, String> {
        return buildMap {
            put(FileSelectionEntry.locationKey, path.toString())
            format?.let { put(FileSelectionEntry.formatKey, it) }
            encoding?.let { put(FileSelectionEntry.encodingKey, it) }
        }
    }


    @Test
    fun directoryResolutionIsOrderedAndFingerprintedOneUnitPerFile() {
        val directory = Files.createTempDirectory("file-source-order")
        val later = directory.resolve("z.csv").also { it.writeText("a\n2\n") }
        val first = directory.resolve("a.csv").also { it.writeText("a\n1\n") }

        val result = resolve(source(directory = directory.toString()))

        assertEquals(listOf(first, later).map(::canonical),
            result.manifest.units.map { it.parts.single().ref.id })
        assertEquals(listOf(first, later).map(::canonical),
            result.resolutionDetails.map { it.ref.id })
        result.manifest.units.forEach { unit ->
            val path = Path.of(unit.parts.single().ref.id)
            assertEquals(1, unit.parts.size)
            assertEquals(DataRole.main, unit.parts.single().role)
            assertNull(unit.parts.single().ref.source)
            assertEquals(Files.size(path).toString(),
                unit.parts.single().ref.attributes[DataRef.sizeKey])
            assertEquals(
                Instant.fromEpochMilliseconds(Files.getLastModifiedTime(path).toMillis()).toString(),
                unit.parts.single().ref.attributes[DataRef.modifiedKey])
        }
    }


    @Test
    fun explicitSelectionWinsAndOverridesDefaults() {
        val directory = Files.createTempDirectory("file-source-explicit")
        directory.resolve("ignored.csv").writeText("ignored")
        val picked = directory.resolve("chosen.tsv").also { it.writeText("a\tb") }
        val formatLookup = lookup(mapOf("Tsv" to ConfiguredDelimitedTestFormats.tsv()))

        val result = resolve(source(
            directory = directory.toString(),
            files = listOf(picked(picked, "Tsv", "ISO-8859-1")),
            formatLookup = formatLookup))
        val part = result.manifest.units.single().parts.single()
        val config = ConfiguredDelimitedReaderCapability.decode(part.resolvedRead.config) as DelimitedReadConfig

        assertEquals(canonical(picked), part.ref.id)
        assertEquals(ConfiguredDelimitedReaderCapability.identity, part.resolvedRead.reader)
        assertEquals("ISO-8859-1", config.characters.charset)
        assertTrue(result.diagnostics.isEmpty())
        assertEquals("Tsv", result.resolutionDetails.single().concreteFormatReference)
        assertEquals(FormatSelectionKind.Explicit, result.resolutionDetails.single().selection)
    }


    @Test
    fun unregisteredProgrammaticSourceFormatStillResolvesStrictly() {
        val file = Files.createTempFile("file-source-programmatic", ".csv")
            .also { it.writeText("left^right\na^b\n") }
        val programmatic = ConfiguredDelimitedTestFormats.csv(delimiter = "^")

        val result = resolve(source(files = listOf(picked(file)), format = programmatic))
        val config = ConfiguredDelimitedReaderCapability.decode(
            result.manifest.units.single().parts.single().resolvedRead.config) as DelimitedReadConfig

        assertEquals("^", config.dialect.delimiter)
        assertEquals(FormatSelectionKind.Explicit, result.resolutionDetails.single().selection)
        assertNull(result.resolutionDetails.single().concreteFormatReference)
    }


    @Test
    fun perFileFormatAndEncodingOverridesApplyIndependently() {
        val tsvFile = Files.createTempFile("file-source-format-only", ".tsv").also { it.writeText("a\tb") }
        val encodedFile = Files.createTempFile("file-source-encoding-only", ".csv").also { it.writeText("a,b") }
        val result = resolve(source(
            files = listOf(
                picked(tsvFile, format = "Tsv"),
                picked(encodedFile, encoding = "ISO-8859-1")),
            formatLookup = lookup(mapOf("Tsv" to ConfiguredDelimitedTestFormats.tsv()))))

        val configs = result.manifest.units.map { unit ->
            ConfiguredDelimitedReaderCapability.decode(unit.parts.single().resolvedRead.config) as DelimitedReadConfig
        }
        assertEquals("\t", configs[0].dialect.delimiter)
        assertEquals("UTF-8", configs[0].characters.charset)
        assertEquals(",", configs[1].dialect.delimiter)
        assertEquals("ISO-8859-1", configs[1].characters.charset)
        assertEquals(listOf(canonical(tsvFile), canonical(encodedFile)),
            result.resolutionDetails.map { it.ref.id })
    }


    @Test
    fun unavailableAndAutomaticPerFileFormatOverridesFailBeforeResolution() {
        val file = Files.createTempFile("file-source-invalid-override", ".csv").also { it.writeText("a,b") }
        val unavailable = assertFailsWith<IllegalArgumentException> {
            resolve(source(
                files = listOf(picked(file, format = "Unavailable")),
                formatLookup = missingFormatLookup))
        }
        assertTrue(unavailable.message!!.contains("Unavailable"), unavailable.message)
        assertTrue(unavailable.message!!.contains(canonical(file)), unavailable.message)

        val automatic = DelegatingFormat(
            ConfiguredDelimitedTestFormats.csv(),
            FormatSelectionKind.Automatic)
        val invalidAutomatic = assertFailsWith<IllegalArgumentException> {
            resolve(source(
                files = listOf(picked(file, format = "Automatic")),
                formatLookup = lookup(mapOf("Automatic" to automatic))))
        }
        assertTrue(invalidAutomatic.message!!.contains("must be concrete"), invalidAutomatic.message)
    }


    @Test
    fun sourceResolutionRunsColdFormatsConcurrentlyButRetainsAuthoredOrder() {
        val taskCount = 12
        val directory = Files.createTempDirectory("file-source-budget")
        val paths = (0 until taskCount).map { index ->
            directory.resolve("$index.csv").also { it.writeText("value\n$index\n") }
        }
        val budgeted = BudgetedFormat()

        val result = resolve(source(
            files = paths.map { picked(it) },
            format = budgeted))

        assertEquals(4, budgeted.peakActive.get())
        assertEquals(paths.map(::canonical), result.manifest.units.map { it.parts.single().ref.id })
        assertEquals(paths.map(::canonical), result.resolutionDetails.map { it.ref.id })
    }


    @Test
    fun explicitSelectionPreservesAuthoredOrder() {
        val directory = Files.createTempDirectory("file-source-explicit-order")
        val alphabeticFirst = directory.resolve("a.csv").also { it.writeText("a") }
        val authoredFirst = directory.resolve("z.csv").also { it.writeText("z") }

        val result = resolve(source(files = listOf(
            picked(authoredFirst),
            picked(alphabeticFirst))))

        assertEquals(
            listOf(canonical(authoredFirst), canonical(alphabeticFirst)),
            result.manifest.units.map { it.parts.single().ref.id })
    }


    @Test
    fun singleFilePreviewDoesNotStatUnrelatedExplicitRows() = runBlocking {
        val selected = Files.createTempFile("file-source-preview", ".csv").also { it.writeText("a\n1\n") }
        // FilePath deliberately permits opaque provider paths that java.nio cannot stat. A full-list implementation
        // would touch this unrelated row and fail before returning the requested preview.
        val unrelatedProviderPath = "\u0000provider-only"
        val source = source(files = listOf(
            picked(selected),
            mapOf(FileSelectionEntry.locationKey to unrelatedProviderPath)))

        val result = source.resolveFile(DirectContext, FileSelectionEntry.ofCollection(picked(selected)))

        assertEquals(canonical(selected), result.manifest.units.single().parts.single().ref.id)
    }


    @Test
    fun persistedBlankOverridesCannotChangeTheCanonicalConfiguredRead() {
        val file = Files.createTempFile("file-source-format", ".csv").also { it.writeText("a") }
        val withDefaults = resolve(source(
            files = listOf(picked(file, "", ""))))
            .manifest.units.single().parts.single()
        val withoutOverrides = resolve(source(files = listOf(picked(file))))
            .manifest.units.single().parts.single()
        assertEquals(withoutOverrides.resolvedRead, withDefaults.resolvedRead)
    }


    @Test
    fun filterUsesContainsAllWordsRatherThanGlobSyntax() {
        val directory = Files.createTempDirectory("file-source-filter")
        directory.resolve("2026-sales.csv").writeText("a")
        directory.resolve("2026-sales.tsv").writeText("a")

        assertEquals(1, resolve(source(directory.toString(), "sales csv")).manifest.units.size)
        assertTrue(resolve(source(directory.toString(), "*.csv")).manifest.units.isEmpty())
    }


    @Test
    fun groupPatternUsesOrderedNamedCapturesOrOneUnnamedCapture() {
        val file = Files.createTempFile("2026-08-sales", ".csv").also { it.writeText("a") }
        val named = resolve(source(
            files = listOf(picked(file)),
            groupPattern = "(?<year>\\d{4})-(?<month>\\d{2})"))
        assertEquals(linkedMapOf("year" to "2026", "month" to "08"),
            named.manifest.units.single().attributes)

        val unnamed = resolve(source(files = listOf(picked(file)), groupPattern = "(sales)"))
        assertEquals(mapOf("group" to "sales"), unnamed.manifest.units.single().attributes)
        assertTrue(resolve(source(files = listOf(picked(file)), groupPattern = "nomatch-(\\d+)"))
            .manifest.units.single().attributes.isEmpty())
        assertTrue(resolve(source(files = listOf(picked(file)), groupPattern = ""))
            .manifest.units.single().attributes.isEmpty())

        assertFailsWith<IllegalArgumentException> {
            resolve(source(files = listOf(picked(file)), groupPattern = "sales"))
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(source(files = listOf(picked(file)), groupPattern = "(2026).*?(sales)"))
        }
    }


    @Test
    fun missingPolicyEitherFailsOrSkipsWithDiagnostic() {
        val missing = Files.createTempDirectory("file-source-missing").resolve("absent.csv")

        val failure = assertFailsWith<IllegalStateException> {
            resolve(source(files = listOf(picked(missing))))
        }
        assertTrue(failure.message!!.contains(canonical(missing)))

        val skipped = resolve(source(files = listOf(picked(missing)), missing = FileDataSource.missingSkip))
        assertTrue(skipped.manifest.units.isEmpty())
        assertEquals(listOf(DataDiagnostic(DataDiagnostic.skipped,
            canonical(missing))), skipped.diagnostics)
    }


    @Test
    fun explicitDirectoryIsRejectedAsNotAFile() {
        val directory = Files.createTempDirectory("file-source-directory")
        val failure = assertFailsWith<IllegalStateException> {
            resolve(source(files = listOf(picked(directory))))
        }
        assertTrue(failure.message!!.contains("Expected file"))
    }


    @Test
    fun declaredSchemaPublishesOrderedTypedFieldsForMainRoleAndBothCodecs() {
        val schema = DataSchemaDocument(DataSchemaFieldListSpec(linkedMapOf(
            "city" to DataSchemaFieldSpec(TypeMetadata.string),
            "amount" to DataSchemaFieldSpec(TypeMetadata.int),
            "note" to DataSchemaFieldSpec(TypeMetadata(
                TypeMetadata.string.className,
                TypeMetadata.string.generics,
                true)))))
        val source = source(schema = schema)
        val shape = source.staticShape(null)!!

        assertEquals(
            listOf("city", "amount", "note"),
            LegacyDataShapeBridge.headerOrNull(shape)!!.values.map { it.text })
        val record = kotlin.test.assertIs<DataType.Record>(shape.itemType.structural)
        assertEquals(ScalarKind.Text, kotlin.test.assertIs<DataType.Scalar>(record.fields[0].type).kind)
        assertEquals(
            ScalarKind.Integer(32),
            kotlin.test.assertIs<DataType.Scalar>(record.fields[1].type).kind)
        assertTrue(kotlin.test.assertIs<DataType.Scalar>(record.fields[2].type).nullable)
        assertEquals(shape, DataShape.ofExecutionValue(shape.asExecutionValue()))
        assertEquals(shape, Json.decodeFromString<DataShape>(Json.encodeToString(shape)))
        assertEquals(source.staticShape(null), source.staticShape(DataRole.main))
        assertNull(source.staticShape(DataRole("preview")))

        val formatOverride = source(
            files = listOf(picked(Files.createTempFile("shape-format-override", ".csv"), format = "Other")),
            schema = schema)
        assertNull(formatOverride.staticShape(null))

        val encodingOverride = source(
            files = listOf(picked(Files.createTempFile("shape-encoding-override", ".csv"), encoding = "UTF-8")),
            schema = schema)
        assertEquals(shape, encodingOverride.staticShape(null))
    }


    private fun lookup(formats: Map<String, ConfiguredRecordFormat>): ConfiguredRecordFormatLookup {
        return ConfiguredRecordFormatLookup { reference ->
            ConfiguredRecordFormatPreflight(
                reference,
                formats[reference] ?: throw IllegalStateException("Unavailable configured format: $reference"))
        }
    }


    private class DelegatingFormat(
        private val delegate: ConfiguredRecordFormat,
        override val selectionKind: FormatSelectionKind
    ): ConfiguredRecordFormat by delegate


    private class BudgetedFormat: ConfiguredRecordFormat by ConfiguredDelimitedTestFormats.csv() {
        override val selectionKind = FormatSelectionKind.Automatic
        val peakActive = AtomicInteger()
        private val active = AtomicInteger()


        override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
            val permit = request.budget.acquireColdPart()
            val activeNow = active.incrementAndGet()
            peakActive.updateAndGet { maxOf(it, activeNow) }
            try {
                delay(probeDelayMillis)
                val resolved = ConfiguredDelimitedTestFormats.csv().resolve(request)
                permit.completeSuccess()
                return resolved.copy(detail = FormatResolutionDetail(
                    request.ref,
                    "test#CSV",
                    "CSV",
                    FormatSelectionKind.Automatic,
                    FormatResolutionBasis.Content,
                    "Test content matched CSV"))
            }
            finally {
                active.decrementAndGet()
                permit.close()
            }
        }
    }


    companion object {
        private const val probeDelayMillis = 10L
        private val missingFormatLookup = ConfiguredRecordFormatLookup { reference ->
            throw IllegalArgumentException("Unavailable configured format: $reference")
        }
    }
}
