package tech.kzen.auto.server.objects.report.pipeline.output
//
//import tech.kzen.auto.common.paradigm.task.api.TaskHandle
//import tech.kzen.auto.server.objects.report.model.ReportRunContext
//import tech.kzen.auto.server.objects.report.model.ReportRunSignature
//import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord
//import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
//import tech.kzen.auto.server.objects.report.pipeline.output.flat.IndexedCsvTable
//import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker
//import java.nio.file.Files
//import java.nio.file.Path
//
//
//class ExportReportOutput(
//    initialReportRunContext: ReportRunContext,
//    private val runDir: Path,
//    private val taskHandle: TaskHandle?,
//    private val progress: ReportProgressTracker?
//) {
//    //-----------------------------------------------------------------------------------------------------------------
////    private data class PreviewRequest(
////        val reportRunContext: ReportRunContext,
////        val outputSpec: OutputExploreSpec
////    )
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private val reportRunSignature: ReportRunSignature = initialReportRunContext.toSignature()
//
//    @Suppress("JoinDeclarationAndAssignment")
//    private val indexedCsvTable: IndexedCsvTable?
//
//
////    @Volatile
////    private var previewRequest: PreviewRequest? = null
////
////    @Volatile
////    private var previewResponse = CompletableFuture<OutputInfo>()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    init {
//        indexedCsvTable =
//            if (! Files.exists(runDir)) {
//                null
//            }
//            else {
//                if (reportRunSignature.hasPivot()) {
//                    TODO("Pivot export not implemented (yet)")
//                }
//
//                IndexedCsvTable(reportRunSignature.inputAndFormulaColumns, runDir)
//            }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun add(recordRow: FlatFileRecord, header: RecordHeader) {
//        indexedCsvTable!!.add(recordRow, header)
//        progress?.nextOutput(1L)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
////    @Synchronized
////    fun preview(
////        reportRunContext: ReportRunContext,
////        outputSpec: OutputExploreSpec,
////        reportWorkPool: ReportWorkPool
////    ): OutputInfo {
////        if (! Files.exists(runDir)) {
////            return missingOutputInfo(reportRunContext, runDir)
////        }
////
////        return if (taskHandle == null) {
////            previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
////        }
////        else {
////            val requestHandle = PreviewRequest(reportRunContext, outputSpec)
////
////            previewRequest = requestHandle
////
////            var response: OutputInfo? = null
////            while (response == null) {
////                response =
////                    try {
////                        previewResponse.get(1, TimeUnit.SECONDS)
////                    }
////                    catch (e: TimeoutException) {
////                        if (taskHandle.isTerminated()) {
////                            usePassive(reportRunContext, runDir) {
////                                it.previewInCurrentThread(reportRunContext, outputSpec, reportWorkPool)
////                            }
////                        }
////                        else {
////                            continue
////                        }
////                    }
////            }
////
////            previewResponse = CompletableFuture()
////
////            response
////        }
////    }
//
//
////    private fun previewInCurrentThread(
////        reportRunContext: ReportRunContext,
////        outputSpec: OutputExploreSpec,
////        reportWorkPool: ReportWorkPool
////    ): OutputInfo {
////        val zeroBasedPreview = outputSpec.previewStartZeroBased()
////
//////        val fileTime = Files.getLastModifiedTime(runDir)
//////        val formattedTime = formatTime(fileTime.toInstant())
////
////        var rowCount: Long = -1
////        var preview: OutputPreview? = null
////        var statusOverride: OutputStatus? = null
////
////        try {
////            rowCount = indexedCsvTable.rowCount()
////            preview = indexedCsvTable.preview(
////                zeroBasedPreview, outputSpec.previewCount)
////        }
////        catch (e: Exception) {
////            if (rowCount == -1L) {
////                rowCount = 0
////            }
////            if (preview == null) {
////                preview =
////                    indexedCsvTable?.corruptPreview(zeroBasedPreview)
////            }
////
////            statusOverride = OutputStatus.Failed
////        }
////
////        val status = reportWorkPool.readRunStatus(reportRunSignature)
////
////        return OutputInfo(
////            runDir.toAbsolutePath().normalize().toString(),
////            OutputTableInfo(
////                saveInfo.message,
////                rowCount,
////                preview),
////            statusOverride ?: status)
////    }
//
////    fun handlePreviewRequest(reportWorkPool: ReportWorkPool) {
////        val request = previewRequest
////            ?: return
////
////        val outputInfo = previewInCurrentThread(
////            request.reportRunContext, request.outputSpec, reportWorkPool)
////
////        previewResponse.complete(outputInfo)
////        previewRequest = null
////    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun close(error: Boolean) {
//        indexedCsvTable?.close(error)
//    }
//}