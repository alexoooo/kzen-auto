package tech.kzen.auto.server.objects.report.exec.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputTableInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.objects.report.exec.output.pivot.PivotBuilder
import tech.kzen.auto.server.objects.report.exec.trace.ReportOutputTrace
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CompletableFuture


// TODO: optimize save csv generation
class TableReportOutput(
    private val initialReportRunContext: ReportRunContext,
    private val progress: ReportOutputTrace?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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


        fun downloadCsvOffline(
            reportRunContext: ReportRunContext
        ): InputStream {
            return when (reportRunContext.analysis.type) {
                AnalysisType.FlatData ->
                    IndexedCsvTable.downloadCsvOffline(reportRunContext.runDir)

                AnalysisType.PivotTable ->
                    PivotBuilder.downloadCsvOffline(reportRunContext)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
//    private val reportRunSignature: ReportRunSignature = initialReportRunContext.toSignature()

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
            if (initialReportRunContext.analysis.type == AnalysisType.FlatData) {
                indexedCsvTable = IndexedCsvTable(
                    initialReportRunContext.analysisColumnInfo.filteredColumns(),
                    initialReportRunContext.runDir)
                pivotBuilder = null
            }
            else {
                indexedCsvTable = null

                val pivotSpec = initialReportRunContext.analysis.pivot
                pivotBuilder = PivotBuilder.create(
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
    private fun previewInCurrentThread(
        pivotValueTableSpec: PivotValueTableSpec,
        start: Long,
        count: Int
    ): OutputTableInfo? {
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

        return OutputTableInfo(
            rowCount,
            preview)
    }


    fun handlePreviewRequest() {
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