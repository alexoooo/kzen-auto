package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.pipeline.ProcessorDatasetPipeline
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.DateTimeUtils
import java.nio.file.Path


// https://javadoc.io/static/com.conversantmedia/disruptor/1.2.9/com/conversantmedia/util/concurrent/PushPullConcurrentQueue.html
// http://lmax-exchange.github.io/disruptor/user-guide/index.html
class ReportRunAction(
    private val reportWorkPool: ReportWorkPool
){
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ReportRunAction::class.java)
    private val mimeTypeCsv = "text/csv"


    //-----------------------------------------------------------------------------------------------------------------
    fun formulaValidation(
        formulaSpec: FormulaSpec,
        flatHeaderListing: HeaderListing,
        modelType: ClassName,
        classLoader: ClassLoader
    ): ExecutionResult {
        val errors: Map<String, String> = formulaSpec
            .formulas
            .mapValues { formula ->
                ServerContext.calculatedColumnEval.validate(
                    formula.key,
                    formula.value,
                    flatHeaderListing,
                    modelType,
                    classLoader)
            }
            .filterValues { error -> error != null }
            .mapValues { e -> e.value!! }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(errors))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun summaryView(
        objectLocation: ObjectLocation,
        reportRunContext: ReportRunContext,
        runDir: Path
    ): ExecutionResult {
        val activeReportHandle = ServerContext
            .modelTaskRepository
            .lookupActive(objectLocation)
            .singleOrNull()
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ProcessorDatasetPipeline }

        val tableSummary =
            activeReportHandle?.summaryView()
                ?: ReportSummary(reportRunContext, runDir, null).view()

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(tableSummary.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun outputInfo(
        objectLocation: ObjectLocation,
        runContext: ReportRunContext,
        runDir: Path,
        outputSpec: OutputExploreSpec
    ): ExecutionResult {
        val activeReportHandle = ServerContext
            .modelTaskRepository
            .lookupActive(objectLocation)
            .singleOrNull()
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ProcessorDatasetPipeline }

        val outputInfo =
            activeReportHandle?.activeOutputInfo(runContext, outputSpec)
                ?: ProcessorDatasetPipeline.passiveOutputInfo(runContext, runDir, outputSpec, reportWorkPool)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(outputInfo.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun outputSave(
        runContext: ReportRunContext,
        runDir: Path,
        outputSpec: OutputExploreSpec
    ): ExecutionResult {
        return try {
            val outPath = ProcessorDatasetPipeline.passiveSave(runContext, runDir, outputSpec, reportWorkPool)
            ExecutionSuccess(
                ExecutionValue.of(outPath.toString()),
                NullExecutionValue)
        }
        catch (e: Exception) {
            ExecutionFailure("Not found: $runDir")
        }
    }


    fun outputDownload(
        runContext: ReportRunContext,
        runDir: Path,
        outputSpec: OutputExploreSpec,
        mainLocation: ObjectLocation
    ): ExecutionDownloadResult {
        val filenamePrefix = FormatUtils.sanitizeFilename(mainLocation.documentPath.name.value)
        val filenameSuffix = DateTimeUtils.filenameTimestamp()
        val filename = filenamePrefix + "_" + filenameSuffix + ".csv"

        val inputStream = ProcessorDatasetPipeline.passiveDownload(runContext, runDir, outputSpec, reportWorkPool)
        return ExecutionDownloadResult(
            inputStream,
            filename,
            mimeTypeCsv
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun delete(
        runDir: Path
    ): ExecutionResult {
        return try {
            ReportWorkPool.deleteDir(runDir)
            ExecutionSuccess.empty
        }
        catch (e: Exception) {
            ExecutionFailure(e.message ?: "error")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startReport(
//        classLoaderHandle: ClassLoaderHandle,
        reportRunContext: ReportRunContext,
        runDir: Path,
        taskHandle: TaskHandle
    ): ProcessorDatasetPipeline {
        logger.info("Starting: $runDir | $reportRunContext")

        val outputValue = ExecutionValue.of(runDir.toString())

        taskHandle.update(ExecutionSuccess.ofValue(outputValue))

        val reportHandle = ProcessorDatasetPipeline(
            /*classLoaderHandle,*/ reportRunContext, runDir, reportWorkPool, taskHandle)

        Thread {
            processSync(
                taskHandle, reportHandle, runDir)
        }.start()

        logger.info("Started: {} | {}", runDir, reportRunContext)

        return reportHandle
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        taskHandle: TaskHandle,
        reportHandle: ProcessorDatasetPipeline,
        runDir: Path
    ) {
        try {
            reportHandle.run()

            if (taskHandle.stopRequested()) {
                reportWorkPool.updateRunStatus(runDir, OutputStatus.Cancelled)
            }
            else {
                reportWorkPool.updateRunStatus(runDir, OutputStatus.Done)
            }

            taskHandle.completeWithPartialResult()
        }
        catch (e: Throwable) {
            logger.warn("Data processing failed", e)

            reportWorkPool.updateRunStatus(runDir, OutputStatus.Failed)

            taskHandle.terminalFailure(ExecutionFailure.ofException(
                "Unable to process - ", e))

            taskHandle.completeWithPartialResult()
        }
    }
}