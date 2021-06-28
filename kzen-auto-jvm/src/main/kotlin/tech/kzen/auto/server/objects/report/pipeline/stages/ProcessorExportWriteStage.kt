package tech.kzen.auto.server.objects.report.pipeline.stages
//
//import com.lmax.disruptor.EventHandler
//import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
//import tech.kzen.auto.server.objects.report.pipeline.output.export.CompressedExportWriter
//
//
//class ProcessorExportWriteStage(
//    val writer: CompressedExportWriter,
////    private val initialReportRunContext: ReportRunContext,
////    private val runDir: Path,
////    private val reportWorkPool: ReportWorkPool,
////    private val taskHandle: TaskHandle
//):
//    EventHandler<ProcessorOutputEvent<*>>
//{
//    //-----------------------------------------------------------------------------------------------------------------
////    private data class PreviewRequest(
////        val reportRunContext: ReportRunContext,
////        val outputSpec: OutputExploreSpec
////    )
//
//
//    //-----------------------------------------------------------------------------------------------------------------
////    @Volatile
////    private var previewRequest: PreviewRequest? = null
////
////    @Volatile
////    private var previewResponse = CompletableFuture<OutputInfo>()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
//        if (endOfBatch) {
//            // NB: must be done regardless of filterAllow to avoid lock due to starvation
////            handlePreviewRequest(reportWorkPool)
//        }
//
//        if (event.skip) {
//            return
//        }
//
////        tableReportOutput.add(event.row, event.header.value)
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
////            return ExportReportOutput.missingOutputInfo(reportRunContext, runDir)
////        }
////        val requestHandle = PreviewRequest(reportRunContext, outputSpec)
////
////        previewRequest = requestHandle
////
////        var response: OutputInfo? = null
////        while (response == null) {
////            response =
////                try {
////                    previewResponse.get(1, TimeUnit.SECONDS)
////                }
////                catch (e: TimeoutException) {
////                    if (taskHandle.isTerminated()) {
////                        OutputInfo()
////                    }
////                    else {
////                        continue
////                    }
////                }
////        }
////
////        previewResponse = CompletableFuture()
////
////        return response
////    }
//
//
////    private fun previewInCurrentThread(
////        reportRunContext: ReportRunContext,
////        outputSpec: OutputExploreSpec,
////        reportWorkPool: ReportWorkPool
////    ): OutputInfo {
////        val status = reportWorkPool.readRunStatus(initialReportRunContext.toSignature())
////
////        return OutputInfo(
////            runDir.toAbsolutePath().normalize().toString(),
////            null,
////            OutputExportInfo("hello"),
////            status)
////    }
//
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
//    fun close() {
//        writer.closeGroup()
//    }
//}