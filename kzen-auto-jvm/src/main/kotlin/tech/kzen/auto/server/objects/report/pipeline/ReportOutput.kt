package tech.kzen.auto.server.objects.report.pipeline

import org.apache.commons.csv.CSVFormat
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.filter.IndexedCsvTable
import tech.kzen.auto.server.objects.report.model.RecordItem
import tech.kzen.auto.server.objects.report.model.ReportRunSignature
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pivot.PivotBuilder
import tech.kzen.auto.server.objects.report.pivot.row.RowIndex
import tech.kzen.auto.server.objects.report.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.report.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.report.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.report.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.report.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.report.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.report.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.report.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.report.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.report.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.report.pivot.store.FileOffsetStore
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class ReportOutput(
    initialReportRunSpec: ReportRunSpec,
    private val runDir: Path,
    private val taskHandle: TaskHandle?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


        private fun headerNames(reportRunSpec: ReportRunSpec): List<String> {
            val runSignature = reportRunSpec.toSignature()

            return when {
                runSignature.hasPivot() ->
                    PivotBuilder.ExportSignature.of(
                        reportRunSpec.pivot.rows.toList(),
                        reportRunSpec.pivot.values
                    ).header

                else ->
                    runSignature.columnNames
            }
        }


        private fun missingOutputInfo(reportRunSpec: ReportRunSpec, runDir: Path): OutputInfo {
            val header = headerNames(reportRunSpec)

            return OutputInfo(
                "Missing, will be created: $runDir",
                null,
                0L,
                OutputPreview(header, listOf(), 0L),
                OutputStatus.Missing
            )
        }


        private fun filePivot(
            rows: Set<String>,
            values: Set<String>,
            tempDir: Path
        ): PivotBuilder {
            val rowTextContentFile = tempDir.resolve("row-text-value.bin")
            val rowTextIndexFile= tempDir.resolve("row-text-index.bin")
            val rowSignatureFile = tempDir.resolve("row-signature.bin")
            val valueStatisticsFile = tempDir.resolve("value-statistics.bin")
            val rowValueDigestDir = tempDir.resolve("row-text-digest")
            val rowSignatureDigestDir = tempDir.resolve("row-signature-digest")

            val rowValueDigestIndex =
                H2DigestIndex(rowValueDigestDir)

            val textOffsetStore = BufferedOffsetStore(
                FileOffsetStore(rowTextIndexFile)
            )

            val indexedTextStore = BufferedIndexedTextStore(
                FileIndexedTextStore(rowTextContentFile, textOffsetStore)
            )

            val rowValueIndex = StoreRowValueIndex(
                rowValueDigestIndex, indexedTextStore)

            val rowSignatureDigestIndex =
                H2DigestIndex(rowSignatureDigestDir)

            val indexedSignatureStore = BufferedIndexedSignatureStore(
                FileIndexedSignatureStore(rowSignatureFile, rows.size)
            )

            val rowSignatureIndex = StoreRowSignatureIndex(
                rowSignatureDigestIndex, indexedSignatureStore)

            val valueStatistics = BufferedValueStatistics(
                FileValueStatisticsStore(valueStatisticsFile, values.size)
            )

            return PivotBuilder(
                rows,
                values,
                RowIndex(rowValueIndex, rowSignatureIndex),
                valueStatistics)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class SaveInfo(
        val path: Path,
        val defaultPath: Path,
        val custom: Boolean,
        val customInvalid: Boolean,
        val message: String
    )


    private data class PreviewRequest(
        val reportRunSpec: ReportRunSpec,
        val outputSpec: OutputSpec
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val reportRunSignature: ReportRunSignature

    private val indexedCsvTable: IndexedCsvTable?
    private val pivotBuilder: PivotBuilder?


    @Volatile
    private var previewRequest: PreviewRequest? = null

    @Volatile
    private var previewResponse = CompletableFuture<OutputInfo>()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        reportRunSignature = initialReportRunSpec.toSignature()

        if (! Files.exists(runDir)) {
            pivotBuilder = null
            indexedCsvTable = null
        }
        else {
            if (! reportRunSignature.hasPivot()) {
                indexedCsvTable = IndexedCsvTable(reportRunSignature.columnNames, runDir)
                pivotBuilder = null
            }
            else {
                indexedCsvTable = null

                val pivotSpec = initialReportRunSpec.pivot
                pivotBuilder = filePivot(pivotSpec.rows, pivotSpec.values.columns.keys, runDir)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(row: RecordItem) {
        handlePreviewRequest()

        if (indexedCsvTable != null) {
            val values = row.getOrEmptyAll(reportRunSignature.columnNames)
            indexedCsvTable.add(values)
        }
        else {
            pivotBuilder!!.add(row)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun preview(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): OutputInfo {
        if (! Files.exists(runDir)) {
            return missingOutputInfo(reportRunSpec, runDir)
        }

        return if (taskHandle == null) {
            previewInCurrentThread(reportRunSpec, outputSpec)
        }
        else {
            val requestHandle = PreviewRequest(reportRunSpec, outputSpec)

            previewRequest = requestHandle

            var response: OutputInfo? = null
            while (response == null) {
                response =
                    try {
                        previewResponse.get(1, TimeUnit.SECONDS)
                    }
                    catch (e: TimeoutException) {
                        continue
                    }
            }

            previewRequest = null
            previewResponse = CompletableFuture()

            response
        }
    }


    private fun previewInCurrentThread(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): OutputInfo {
        val zeroBasedPreview = outputSpec.previewStartZeroBased()

        val fileTime = Files.getLastModifiedTime(runDir)
        val formattedTime = formatTime(fileTime.toInstant())

        val rowCount: Long
        val preview =
            if (indexedCsvTable != null) {
                rowCount = indexedCsvTable.rowCount()
                indexedCsvTable.preview(
                    zeroBasedPreview, outputSpec.previewCount)
            }
            else {
                check(pivotBuilder != null)
                rowCount = pivotBuilder.rowCount()
                pivotBuilder.preview(
                    reportRunSpec.pivot.values, zeroBasedPreview, outputSpec.previewCount)
            }

        val saveInfo = saveInfo(runDir, outputSpec)

        val status = ReportWorkPool.readRunStatus(reportRunSignature)

        return OutputInfo(
            saveInfo.message,
            formattedTime,
            rowCount,
            preview,
            status)
    }


    fun save(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): Path? {
        check(taskHandle == null)

        if (! Files.exists(runDir)) {
            return null
        }

        val runSignature = reportRunSpec.toSignature()

        val saveInfo = saveInfo(runDir, outputSpec)
        val outputPath = saveInfo.path

        if (! runSignature.hasPivot() &&
            saveInfo.path.toAbsolutePath().normalize() ==
            saveInfo.defaultPath.toAbsolutePath().normalize())
        {
            // NB: don't override source of truth
            return outputPath
        }

        Files.newBufferedWriter(outputPath).use { output ->
            val csvPrinter = CSVFormat.DEFAULT.print(output)

            if (indexedCsvTable != null) {
                indexedCsvTable.traverseWithHeader { row ->
                    csvPrinter.printRecord(row)
                }
            }
            else {
                check(pivotBuilder != null)
                pivotBuilder.traverseWithHeader(reportRunSpec.pivot.values) { row ->
                    csvPrinter.printRecord(row)
                }
            }
        }

        return outputPath
    }


    private fun formatTime(instant: Instant): String {
        return instant
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(modifiedFormatter)
    }


    private fun saveInfo(runDir: Path, outputSpec: OutputSpec): SaveInfo {
        val defaultPath = runDir.resolve(IndexedCsvTable.tableFile)

        val customPath: Path?
        val customInvalid: Boolean
        if (outputSpec.savePath.isBlank()) {
            customPath = null
            customInvalid = false
        }
        else {
            customPath = try {
                Paths.get(outputSpec.savePath)
            } catch (e: InvalidPathException) {
                null
            }
            customInvalid = customPath == null
        }

        val pathWithFallback = customPath ?: defaultPath

        val existsMessage =
            if (Files.exists(pathWithFallback)) {
                "already exists"
            }
            else {
                "does not exist (will create)"
            }

        val typeMessage = when {
            customInvalid ->
                "Invalid path provided (using default)"

            customPath == null ->
                "Using default path"

            else ->
                "Using custom path"
        }

        val message = "$typeMessage, $existsMessage: ${pathWithFallback.toAbsolutePath().normalize()}"

        return SaveInfo(
            pathWithFallback,
            defaultPath,
            customPath != null || customInvalid,
            customInvalid,
            message
        )
    }


    private fun handlePreviewRequest() {
        val request = previewRequest
            ?: return

        val outputInfo = previewInCurrentThread(request.reportRunSpec, request.outputSpec)

        previewResponse.complete(outputInfo)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        indexedCsvTable?.close()
        pivotBuilder?.close()
    }
}