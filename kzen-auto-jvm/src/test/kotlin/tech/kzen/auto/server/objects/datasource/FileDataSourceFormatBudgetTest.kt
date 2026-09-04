package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.format.SourceFormatResolutionBudgetFactory
import tech.kzen.auto.server.data.format.SourceFormatResolutionPolicy
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


class FileDataSourceFormatBudgetTest {
    private object DirectContext: DataContext {
        override fun argument(name: String): Any? = null
        override suspend fun <R> blocking(block: () -> R): R = block()
    }

    private val listing = FileListingAction(HostReportDefinitionRepository(emptyList()))


    @Test
    fun warmPartsBypassEveryColdLimitAndStillProduceTheCompleteManifest() = runBlocking {
        val files = inputs("file-source-warm", 6)
        val format = TrackingAutomaticFormat(warm = files.map { canonical(it) }.toSet())
        val result = source(
            files,
            format,
            policy(maximumColdParts = 1, maximumDecodedBytes = 1, overallTimeoutMillis = 100))
            .resolve(DirectContext)

        assertEquals(files.map(::canonical), result.manifest.units.map { it.parts.single().ref.id })
        assertEquals(0, format.coldStarts.get())
        assertEquals(0, format.handlesOpened.get())
    }


    @Test
    fun coldPartLimitFailsTheWholeResolutionAndClosesEveryStartedHandle() = runBlocking {
        val files = inputs("file-source-cold-limit", 6)
        val format = TrackingAutomaticFormat(delayMillis = 25)

        val failure = runCatching {
            source(files, format, policy(maximumColdParts = 5)).resolve(DirectContext)
        }.exceptionOrNull()

        assertLimitFailure(failure, "cold-part limit of 5")
        assertEquals(format.handlesOpened.get(), format.handlesClosed.get())
        assertTrue(format.handlesOpened.get() > 0)
        assertTrue(format.completed.isEmpty(), "a failed aggregate must publish no partial manifest")
    }


    @Test
    fun decodedByteLimitFailsTheWholeResolutionAndClosesEveryHandle() = runBlocking {
        val files = inputs("file-source-byte-limit", 3)
        val format = TrackingAutomaticFormat(decodedBytes = 3)

        val failure = runCatching {
            source(files, format, policy(maximumDecodedBytes = 5)).resolve(DirectContext)
        }.exceptionOrNull()

        assertLimitFailure(failure, "decoded-sample limit of 5 bytes")
        assertEquals(format.handlesOpened.get(), format.handlesClosed.get())
        assertTrue(format.handlesOpened.get() > 0)
    }


    @Test
    fun aggregateDeadlineCancelsEveryPartAndClosesEveryHandle() = runBlocking {
        val files = inputs("file-source-time-limit", 4)
        val format = TrackingAutomaticFormat(waitForever = true)

        val failure = runCatching {
            source(files, format, policy(overallTimeoutMillis = 50)).resolve(DirectContext)
        }.exceptionOrNull()

        assertLimitFailure(failure, "wall-time limit of 50 ms")
        assertEquals(4, format.handlesOpened.get())
        assertEquals(format.handlesOpened.get(), format.handlesClosed.get())
        assertTrue(format.completed.isEmpty())
    }


    @Test
    fun heterogeneousColdPartsKeepIndependentSpecsWithoutMajoritySelection() = runBlocking {
        val directory = Files.createTempDirectory("file-source-no-majority")
        val files = listOf("first.csv", "minority.tsv", "last.csv").map { name ->
            directory.resolve(name).also { it.writeText("left,right\n1,2\n") }
        }
        val format = TrackingAutomaticFormat(delimiterBySuffix = true)

        val result = source(files, format, policy()).resolve(DirectContext)
        val delimiters = result.manifest.units.map { unit ->
            val config = ConfiguredDelimitedReaderCapability.decode(
                unit.parts.single().resolvedRead.config) as DelimitedReadConfig
            config.dialect.delimiter
        }

        assertEquals(listOf(",", "\t", ","), delimiters)
        assertEquals(listOf("test#CSV", "test#TSV", "test#CSV"),
            result.resolutionDetails.map { it.concreteFormatReference })
    }


    private fun source(
        files: List<Path>,
        format: ConfiguredRecordFormat,
        policy: SourceFormatResolutionPolicy
    ): FileDataSource = FileDataSource(
        "",
        "",
        files.map { mapOf(FileSelectionEntry.locationKey to it.toString()) },
        format,
        "",
        FileDataSource.missingFail,
        listing,
        resolutionBudgetFactory = SourceFormatResolutionBudgetFactory(policy))


    private fun inputs(prefix: String, count: Int): List<Path> {
        val directory = Files.createTempDirectory(prefix)
        return (0 until count).map { index ->
            directory.resolve("$index.csv").also { it.writeText("value\n$index\n") }
        }
    }


    private fun policy(
        maximumColdParts: Int = 8,
        maximumDecodedBytes: Long = 1024,
        overallTimeoutMillis: Long = 2_000
    ) = SourceFormatResolutionPolicy(
        maximumConcurrentColdParts = 4,
        maximumColdParts = maximumColdParts,
        maximumDecodedBytes = maximumDecodedBytes,
        overallTimeoutMillis = overallTimeoutMillis)


    private fun canonical(path: Path): String =
        tech.kzen.auto.common.util.data.DataLocation.of(path.toAbsolutePath().normalize().toString()).asString()


    private fun assertLimitFailure(failure: Throwable?, expected: String) {
        val typed = assertIs<IllegalStateException>(failure)
        assertTrue(typed.message.orEmpty().contains(expected), typed.message)
        assertTrue(typed.message.orEmpty().contains("Narrow the file filter"), typed.message)
    }


    private class TrackingAutomaticFormat(
        private val warm: Set<String> = emptySet(),
        private val decodedBytes: Int = 0,
        private val delayMillis: Long = 0,
        private val waitForever: Boolean = false,
        private val delimiterBySuffix: Boolean = false
    ): ConfiguredRecordFormat by ConfiguredDelimitedTestFormats.csv() {
        override val selectionKind = FormatSelectionKind.Automatic
        val coldStarts = AtomicInteger()
        val handlesOpened = AtomicInteger()
        val handlesClosed = AtomicInteger()
        val completed: MutableSet<String> = ConcurrentHashMap.newKeySet()


        override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult {
            if (request.ref.id in warm) return resolved(request, ",", "CSV")

            val permit = request.budget.acquireColdPart()
            coldStarts.incrementAndGet()
            handlesOpened.incrementAndGet()
            try {
                request.budget.chargeDecodedBytes(decodedBytes)
                if (waitForever) awaitCancellation()
                if (delayMillis > 0) delay(delayMillis)
                val delimiter = if (delimiterBySuffix && request.ref.id.endsWith(".tsv")) "\t" else ","
                val label = if (delimiter == "\t") "TSV" else "CSV"
                val result = resolved(request, delimiter, label)
                completed.add(request.ref.id)
                permit.completeSuccess()
                return result
            }
            finally {
                handlesClosed.incrementAndGet()
                permit.close()
            }
        }


        private suspend fun resolved(
            request: FormatResolutionRequest,
            delimiter: String,
            label: String
        ): FormatResolutionResult {
            val delegate = if (delimiter == "\t") {
                ConfiguredDelimitedTestFormats.tsv()
            }
            else {
                ConfiguredDelimitedTestFormats.csv()
            }
            return delegate.resolve(request).copy(detail = FormatResolutionDetail(
                request.ref,
                "test#$label",
                label,
                FormatSelectionKind.Automatic,
                FormatResolutionBasis.Content,
                "Test content matched $label"))
        }
    }
}
