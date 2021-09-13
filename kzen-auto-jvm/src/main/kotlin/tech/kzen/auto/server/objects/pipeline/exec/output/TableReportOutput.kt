package tech.kzen.auto.server.objects.pipeline.exec.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputTableInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.pipeline.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.PivotBuilder
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.RowIndex
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.pipeline.exec.trace.PipelineOutputTrace
import tech.kzen.auto.server.objects.pipeline.model.ReportRunContext
import tech.kzen.auto.server.objects.pipeline.model.ReportRunSignature
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture


// TODO: optimize save csv generation
class TableReportOutput(
    initialReportRunContext: ReportRunContext,
    private val progress: PipelineOutputTrace?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


//        private fun missingOutputInfo(reportRunContext: ReportRunContext): OutputInfo {
//            val header = headerNames(reportRunContext)
//            val runDir = reportRunContext.runDir
//            return OutputInfo(
//                runDir.toAbsolutePath().normalize().toString(),
//                OutputTableInfo(
//                    "Missing, will be created: $runDir",
//                    0L,
//                    OutputPreview(header, listOf(), 0L)),
//                null,
//                OutputStatus.Missing
//            )
//        }


        private fun filePivot(
            rows: HeaderListing,
            values: HeaderListing,
            pivotDir: Path
        ): PivotBuilder {
            val rowTextContentFile = pivotDir.resolve("row-text-value.bin")
            val rowTextIndexFile= pivotDir.resolve("row-text-index.bin")
            val rowSignatureFile = pivotDir.resolve("row-signature.bin")
            val valueStatisticsFile = pivotDir.resolve("value-statistics.bin")
            val rowValueDigestDir = pivotDir.resolve("row-text-digest")
            val rowSignatureDigestDir = pivotDir.resolve("row-signature-digest")

            val rowValueDigestIndex =
                H2DigestIndex(rowValueDigestDir)

            val textOffsetStore = BufferedOffsetStore(
                FileOffsetStore(rowTextIndexFile))

            val indexedTextStore = BufferedIndexedTextStore(
                FileIndexedTextStore(rowTextContentFile, textOffsetStore))

            val rowValueIndex = StoreRowValueIndex(
                rowValueDigestIndex, indexedTextStore)

            val rowSignatureDigestIndex =
                H2DigestIndex(rowSignatureDigestDir)

            val indexedSignatureStore = BufferedIndexedSignatureStore(
                FileIndexedSignatureStore(rowSignatureFile, rows.values.size))

            val rowSignatureIndex = StoreRowSignatureIndex(
                rowSignatureDigestIndex, indexedSignatureStore)

            val valueStatistics = BufferedValueStatistics(
                FileValueStatisticsStore(valueStatisticsFile, values.values.size))

            return PivotBuilder(
                rows,
                values,
                RowIndex(rowValueIndex, rowSignatureIndex),
                valueStatistics)
        }


        private fun <T> usePassive(
            reportRunContext: ReportRunContext,
            user: (TableReportOutput) -> T
        ): T {
            val instance = TableReportOutput(reportRunContext, null)
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


        fun outputInfoOffline(
            reportRunContext: ReportRunContext,
            outputSpec: OutputExploreSpec
        ): OutputTableInfo? {
            return usePassive(reportRunContext) {
                it.previewInCurrentThread(
                    reportRunContext.analysis.pivot.values,
                    outputSpec.previewStartZeroBased(),
                    outputSpec.previewCount)
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


    private data class PreviewResponse(
        val outputTableInfo: OutputTableInfo?
    ) {
        companion object {
            val failed = PreviewResponse(null)
        }
    }


    private data class PreviewRequest(
        val pivotValueTableSpec: PivotValueTableSpec,
        val start: Long,
        val count: Int
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val reportRunSignature: ReportRunSignature = initialReportRunContext.toSignature()

    private val indexedCsvTable: IndexedCsvTable?
    private val pivotBuilder: PivotBuilder?


    @Volatile
    private var previewRequest: PreviewRequest? = null

    @Volatile
    private var previewResponse: CompletableFuture<PreviewResponse>? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        if (! Files.exists(initialReportRunContext.runDir)) {
            pivotBuilder = null
            indexedCsvTable = null
        }
        else {
            if (! reportRunSignature.hasPivot()) {
                indexedCsvTable = IndexedCsvTable(
                    reportRunSignature.inputAndFormulaColumns, initialReportRunContext.runDir)
                pivotBuilder = null
            }
            else {
                indexedCsvTable = null

                val pivotSpec = initialReportRunContext.analysis.pivot
                pivotBuilder = filePivot(
                    pivotSpec.rows,
                    HeaderListing(pivotSpec.values.columns.keys.toList()),
                    initialReportRunContext.runDir)
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
//    @Synchronized
//    fun preview(
//        reportRunContext: ReportRunContext,
//        outputSpec: OutputExploreSpec,
//        reportWorkPool: ReportWorkPool
//    ): OutputInfo {
//        if (! Files.exists(initialReportRunContext.runDir)) {
//            return missingOutputInfo(reportRunContext)
//        }
//
//        return if (taskHandle == null) {
//            previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
//        }
//        else {
//            val requestHandle = PreviewRequest(reportRunContext, outputSpec)
//
//            previewRequest = requestHandle
//
//            var response: OutputInfo? = null
//            while (response == null) {
//                response =
//                    try {
//                        previewResponse.get(1, TimeUnit.SECONDS)
//                    }
//                    catch (e: TimeoutException) {
//                        if (taskHandle.isTerminated()) {
//                            usePassive(reportRunContext) {
//                                it.previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
//                            }
//                        }
//                        else {
//                            continue
//                        }
//                    }
//            }
//
//            previewResponse = CompletableFuture()
//
//            response
//        }
//    }


//    private fun previewInCurrentThread(
//        reportRunContext: ReportRunContext,
//        outputSpec: OutputExploreSpec,
//        reportWorkPool: ReportWorkPool
//    ): OutputTableInfo? {
//        val zeroBasedPreview = outputSpec.previewStartZeroBased()
//
//        var rowCount: Long = -1
//        var preview: OutputPreview? = null
//        var statusOverride: OutputStatus? = null
//
//        try {
//            if (indexedCsvTable != null) {
//                rowCount = indexedCsvTable.rowCount()
//                preview = indexedCsvTable.preview(
//                    zeroBasedPreview, outputSpec.previewCount)
//            }
//            else {
//                check(pivotBuilder != null)
//                rowCount = pivotBuilder.rowCount()
//                preview = pivotBuilder.preview(
//                    reportRunContext.analysis.pivot.values, zeroBasedPreview, outputSpec.previewCount)
//            }
//        }
//        catch (e: Exception) {
//            if (rowCount == -1L) {
//                rowCount = 0
//            }
//            if (preview == null) {
//                preview =
//                    indexedCsvTable?.corruptPreview(zeroBasedPreview)
//                        ?: pivotBuilder!!.corruptPreview(reportRunContext.analysis.pivot.values, zeroBasedPreview)
//            }
//
//            statusOverride = OutputStatus.Failed
//        }
//
//        val saveInfo = saveInfo(initialReportRunContext.runDir, outputSpec)
//
//        return OutputTableInfo(
////            saveInfo.message,
//            rowCount,
//            preview)
//    }


    private fun previewInCurrentThread(
        pivotValueTableSpec: PivotValueTableSpec,
//        outputSpec: OutputExploreSpec
        start: Long,
        count: Int
    ): OutputTableInfo? {
//        val zeroBasedPreview = outputSpec.previewStartZeroBased()

        val rowCount: Long
        val preview: OutputPreview?

        try {
            if (indexedCsvTable != null) {
                rowCount = indexedCsvTable.rowCount()
                preview = indexedCsvTable.preview(
                    start, count)
            }
            else {
                check(pivotBuilder != null)
                rowCount = pivotBuilder.rowCount()
                preview = pivotBuilder.preview(
                    pivotValueTableSpec, start, count)
            }
        }
        catch (e: Exception) {
            return null
        }

//        val saveInfo = saveInfo(initialReportRunContext.runDir, outputSpec)

        return OutputTableInfo(
//            saveInfo.message,
            rowCount,
            preview)
    }


//    fun save(reportRunContext: ReportRunContext, outputSpec: OutputExploreSpec): Path {
////        check(taskHandle == null)
//        check(Files.exists(initialReportRunContext.runDir))
//
//        val saveInfo = saveInfo(initialReportRunContext.runDir, outputSpec)
//        val outputPath = saveInfo.path
//
//        save(reportRunContext, outputSpec, saveInfo.path)
//
//        return outputPath
//    }


//    private fun save(reportRunContext: ReportRunContext, outputSpec: OutputExploreSpec, path: Path) {
//        val runSignature = reportRunContext.toSignature()
//        val saveInfo = saveInfo(initialReportRunContext.runDir, outputSpec)
//
//        if (! runSignature.hasPivot() &&
//            path.toAbsolutePath().normalize() ==
//                saveInfo.defaultPath.toAbsolutePath().normalize())
//        {
//            return
//        }
//
//        // TODO: optimize to be GC-free
//        Files.newBufferedWriter(path).use { output ->
//            val record = FlatFileRecord()
//
//            if (indexedCsvTable != null) {
//                indexedCsvTable.traverseWithHeader { row ->
//                    record.clearWithoutCache()
//                    record.addAll(row)
//                    record.writeCsv(output)
//                    output.write("\r\n")
//                }
//            }
//            else {
//                check(pivotBuilder != null)
//                pivotBuilder.traverseWithHeader(reportRunContext.analysis.pivot.values) { row ->
//                    record.clearWithoutCache()
//                    record.addAll(row)
//                    record.writeCsv(output)
//                    output.write("\r\n")
//                }
//            }
//        }
//    }
//
//
//    fun download(reportRunContext: ReportRunContext, outputSpec: OutputExploreSpec): InputStream {
////        check(taskHandle == null)
//        check(Files.exists(initialReportRunContext.runDir))
//
////        val saveInfo = saveInfo(initialReportRunContext.runDir, outputSpec)
//
////        val dataPath = initialReportRunContext.runDir.resolve(IndexedCsvTable.tableFile)
////        save(reportRunContext, outputSpec, dataPath)
//
//        return Files.newInputStream(dataPath)
//    }


//    private fun formatTime(instant: Instant): String {
//        return instant
//            .atZone(ZoneId.systemDefault())
//            .toLocalDateTime()
//            .format(modifiedFormatter)
//    }


//    private fun saveInfo(runDir: Path, outputSpec: OutputExploreSpec): SaveInfo {
//        val defaultPath = runDir.resolve(IndexedCsvTable.tableFile)
//
//        val customPath: Path?
//        val customInvalid: Boolean
//        if (outputSpec.savePath.isBlank()) {
//            customPath = null
//            customInvalid = false
//        }
//        else {
//            customPath = try {
//                Paths.get(outputSpec.savePath)
//            } catch (e: InvalidPathException) {
//                null
//            }
//            customInvalid = customPath == null
//        }
//
//        val pathWithFallback = customPath ?: defaultPath
//
//        val existsMessage =
//            if (Files.exists(pathWithFallback)) {
//                "already exists"
//            }
//            else {
//                "does not exist (will create)"
//            }
//
//        val typeMessage = when {
//            customInvalid ->
//                "Invalid path provided (using default)"
//
//            customPath == null ->
//                "Using default path"
//
//            else ->
//                "Using custom path"
//        }
//
//        val message = "$typeMessage, $existsMessage: ${pathWithFallback.toAbsolutePath().normalize()}"
//
//        return SaveInfo(
//            pathWithFallback,
//            defaultPath,
//            customPath != null || customInvalid,
//            customInvalid,
//            message
//        )
//    }


    fun handlePreviewRequest(/*reportWorkPool: ReportWorkPool*/) {
        val request = previewRequest
            ?: return

        val outputInfo = previewInCurrentThread(
            request.pivotValueTableSpec, request.start, request.count)

        previewResponse!!.complete(PreviewResponse(outputInfo))
        previewRequest = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun previewFromOtherThread(
        pivotValueTableSpec: PivotValueTableSpec,
        start: Long,
        count: Int
    ): OutputTableInfo? {
        previewResponse = CompletableFuture()
        previewRequest = PreviewRequest(
            pivotValueTableSpec, start, count)

        val response = previewResponse!!.get()

        return response.outputTableInfo
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        previewResponse?.complete(PreviewResponse.failed)
        indexedCsvTable?.close(error)
        pivotBuilder?.close()
    }
}