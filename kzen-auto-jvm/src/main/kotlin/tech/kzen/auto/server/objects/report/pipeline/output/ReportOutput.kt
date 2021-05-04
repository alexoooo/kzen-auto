package tech.kzen.auto.server.objects.report.pipeline.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.model.ReportRunSignature
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.output.flat.IndexedCsvTable
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.PivotBuilder
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.RowIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker
import java.io.InputStream
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


// TODO: optimize save csv generation
class ReportOutput(
    initialReportRunContext: ReportRunContext,
    private val runDir: Path,
    private val taskHandle: TaskHandle?,
    private val progress: ReportProgressTracker?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


        private fun headerNames(reportRunContext: ReportRunContext): HeaderListing {
            val runSignature = reportRunContext.toSignature()

            return when {
                runSignature.hasPivot() ->
                    PivotBuilder.ExportSignature.of(
                        reportRunContext.pivot.rows,
                        reportRunContext.pivot.values
                    ).header

                else ->
                    runSignature.inputAndFormulaColumns
            }
        }


        private fun missingOutputInfo(reportRunContext: ReportRunContext, runDir: Path): OutputInfo {
            val header = headerNames(reportRunContext)

            return OutputInfo(
                runDir.toAbsolutePath().normalize().toString(),
                "Missing, will be created: $runDir",
                null,
                0L,
                OutputPreview(header, listOf(), 0L),
                OutputStatus.Missing
            )
        }


        private fun filePivot(
            rows: HeaderListing,
            values: HeaderListing,
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
                FileIndexedSignatureStore(rowSignatureFile, rows.values.size)
            )

            val rowSignatureIndex = StoreRowSignatureIndex(
                rowSignatureDigestIndex, indexedSignatureStore)

            val valueStatistics = BufferedValueStatistics(
                FileValueStatisticsStore(valueStatisticsFile, values.values.size)
            )

            return PivotBuilder(
                rows,
                values,
                RowIndex(rowValueIndex, rowSignatureIndex),
                valueStatistics)
        }


        private fun <T> usePassive(
            reportRunContext: ReportRunContext,
            runDir: Path,
            user: (ReportOutput) -> T
        ): T {
            val instance = ReportOutput(reportRunContext, runDir, null, null)
            var error = true
            return try {
                val value = user(instance)
                error = false
                value
            }
            finally {
                instance.close(error)
            }
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
        val reportRunContext: ReportRunContext,
        val outputSpec: OutputSpec
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val reportRunSignature: ReportRunSignature = initialReportRunContext.toSignature()

    private val indexedCsvTable: IndexedCsvTable?
    private val pivotBuilder: PivotBuilder?


    @Volatile
    private var previewRequest: PreviewRequest? = null

    @Volatile
    private var previewResponse = CompletableFuture<OutputInfo>()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        if (! Files.exists(runDir)) {
            pivotBuilder = null
            indexedCsvTable = null
        }
        else {
            if (! reportRunSignature.hasPivot()) {
                indexedCsvTable = IndexedCsvTable(reportRunSignature.inputAndFormulaColumns, runDir)
                pivotBuilder = null
            }
            else {
                indexedCsvTable = null

                val pivotSpec = initialReportRunContext.pivot
                pivotBuilder = filePivot(
                    pivotSpec.rows,
                    HeaderListing(pivotSpec.values.columns.keys.toList()),
                    runDir)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordRow: FlatFileRecord, header: RecordHeader) {
        if (indexedCsvTable != null) {
            indexedCsvTable.add(recordRow, header)
            progress?.nextOutput(1L)
        }
        else {
            val newRow = pivotBuilder!!.add(recordRow, header)
            if (newRow) {
                progress?.nextOutput(1L)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun preview(
        reportRunContext: ReportRunContext,
        outputSpec: OutputSpec,
        reportWorkPool: ReportWorkPool
    ): OutputInfo {
        if (! Files.exists(runDir)) {
            return missingOutputInfo(reportRunContext, runDir)
        }

        return if (taskHandle == null) {
            previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
        }
        else {
            val requestHandle = PreviewRequest(reportRunContext, outputSpec)

            previewRequest = requestHandle

            var response: OutputInfo? = null
            while (response == null) {
                response =
                    try {
                        previewResponse.get(1, TimeUnit.SECONDS)
                    }
                    catch (e: TimeoutException) {
                        if (taskHandle.isTerminated()) {
                            usePassive(reportRunContext, runDir) {
                                it.previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
                            }
                        }
                        else {
                            continue
                        }
                    }
            }

            previewResponse = CompletableFuture()

            response
        }
    }


    private fun previewInCurrentThread(
        reportRunContext: ReportRunContext,
        outputSpec: OutputSpec,
        reportWorkPool: ReportWorkPool
    ): OutputInfo {
        val zeroBasedPreview = outputSpec.previewStartZeroBased()

        val fileTime = Files.getLastModifiedTime(runDir)
        val formattedTime = formatTime(fileTime.toInstant())

        var rowCount: Long = -1
        var preview: OutputPreview? = null
        var statusOverride: OutputStatus? = null

        try {
            if (indexedCsvTable != null) {
                rowCount = indexedCsvTable.rowCount()
                preview = indexedCsvTable.preview(
                    zeroBasedPreview, outputSpec.previewCount)
            }
            else {
                check(pivotBuilder != null)
                rowCount = pivotBuilder.rowCount()
                preview = pivotBuilder.preview(
                    reportRunContext.pivot.values, zeroBasedPreview, outputSpec.previewCount)
            }
        }
        catch (e: Exception) {
            if (rowCount == -1L) {
                rowCount = 0
            }
            if (preview == null) {
                preview =
                    indexedCsvTable?.corruptPreview(zeroBasedPreview)
                        ?: pivotBuilder!!.corruptPreview(reportRunContext.pivot.values, zeroBasedPreview)
            }

            statusOverride = OutputStatus.Failed
        }

        val saveInfo = saveInfo(runDir, outputSpec)

        val status = reportWorkPool.readRunStatus(reportRunSignature)

        return OutputInfo(
            runDir.toAbsolutePath().normalize().toString(),
            saveInfo.message,
            formattedTime,
            rowCount,
            preview,
            statusOverride ?: status)
    }


    fun save(reportRunContext: ReportRunContext, outputSpec: OutputSpec): Path {
        check(taskHandle == null)
        check(Files.exists(runDir))

        val saveInfo = saveInfo(runDir, outputSpec)
        val outputPath = saveInfo.path

        save(reportRunContext, outputSpec, saveInfo.path)

        return outputPath
    }


    private fun save(reportRunContext: ReportRunContext, outputSpec: OutputSpec, path: Path) {
        val runSignature = reportRunContext.toSignature()
        val saveInfo = saveInfo(runDir, outputSpec)

        if (! runSignature.hasPivot() &&
            path.toAbsolutePath().normalize() ==
                saveInfo.defaultPath.toAbsolutePath().normalize())
        {
            return
        }

        // TODO: optimize to be GC-free
        Files.newBufferedWriter(path).use { output ->
            val record = FlatFileRecord()

            if (indexedCsvTable != null) {
                indexedCsvTable.traverseWithHeader { row ->
                    record.clearWithoutCache()
                    record.addAll(row)
                    record.writeCsv(output)
                    output.write("\r\n")
                }
            }
            else {
                check(pivotBuilder != null)
                pivotBuilder.traverseWithHeader(reportRunContext.pivot.values) { row ->
                    record.clearWithoutCache()
                    record.addAll(row)
                    record.writeCsv(output)
                    output.write("\r\n")
                }
            }
        }
    }


    fun download(reportRunContext: ReportRunContext, outputSpec: OutputSpec): InputStream {
        check(taskHandle == null)
        check(Files.exists(runDir))

        val saveInfo = saveInfo(runDir, outputSpec)

        save(reportRunContext, outputSpec, saveInfo.defaultPath)

        return Files.newInputStream(saveInfo.defaultPath)
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


    fun handlePreviewRequest(reportWorkPool: ReportWorkPool) {
        val request = previewRequest
            ?: return

        val outputInfo = previewInCurrentThread(
            request.reportRunContext, request.outputSpec, reportWorkPool)

        previewResponse.complete(outputInfo)
        previewRequest = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        indexedCsvTable?.close(error)
        pivotBuilder?.close()
    }
}