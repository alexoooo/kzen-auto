package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.report.model.ReportFormulaSignature
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ProcessorDatasetPipeline
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.util.AutoJvmUtils
import tech.kzen.lib.common.model.locate.ObjectLocation
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
    suspend fun formulaValidation(
//        objectLocation: ObjectLocation,
        formulaSignature: ReportFormulaSignature//,
//        runDir: Path
    ): ExecutionResult {
        val errors: Map<String, String> = formulaSignature
            .formula
            .formulas
            .mapValues { formula ->
                ServerContext.calculatedColumnEval.validate(
                    formula.key, formula.value, formulaSignature.columnNames)
            }
            .filterValues { error -> error != null }
            .mapValues { e -> e.value!! }

//        val activeReportHandle = ServerContext
//            .modelTaskRepository
//            .lookupActive(objectLocation)
//            .singleOrNull()
//            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ReportHandle }
//
//        val tableSummary =
//            activeReportHandle?.summaryView()
//                ?: ReportSummary(reportRunSpec, runDir, null).view()

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(errors))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun summaryView(
        objectLocation: ObjectLocation,
        reportRunSpec: ReportRunSpec,
        runDir: Path
    ): ExecutionResult {
        val activeReportHandle = ServerContext
            .modelTaskRepository
            .lookupActive(objectLocation)
            .singleOrNull()
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ProcessorDatasetPipeline }

        val tableSummary =
            activeReportHandle?.summaryView()
                ?: ReportSummary(reportRunSpec, runDir, null).view()

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(tableSummary.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun outputPreview(
        objectLocation: ObjectLocation,
        runSpec: ReportRunSpec,
        runDir: Path,
        outputSpec: OutputSpec
    ): ExecutionResult {
        val activeReportHandle = ServerContext
            .modelTaskRepository
            .lookupActive(objectLocation)
            .singleOrNull()
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ProcessorDatasetPipeline }

        val outputInfo =
            activeReportHandle?.outputPreview(runSpec, outputSpec)
                ?: ProcessorDatasetPipeline.passivePreview(runSpec, runDir, outputSpec, reportWorkPool)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(outputInfo.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun outputSave(
        runSpec: ReportRunSpec,
        runDir: Path,
        outputSpec: OutputSpec
    ): ExecutionResult {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val outPath = ProcessorDatasetPipeline.passiveSave(runSpec, runDir, outputSpec, reportWorkPool)

        return when (outPath) {
            null ->
                ExecutionFailure("Not found: $runDir")

            else -> ExecutionSuccess(
                ExecutionValue.of(outPath.toString()),
                NullExecutionValue)
        }
    }


    fun outputDownload(
        runSpec: ReportRunSpec,
        runDir: Path,
        outputSpec: OutputSpec,
        mainLocation: ObjectLocation
    ): ExecutionDownloadResult {
        val filenamePrefix = AutoJvmUtils.sanitizeFilename(mainLocation.documentPath.name.value)
        val filenameSuffix = DateTimeUtils.filenameTimestamp()
        val filename = filenamePrefix + "_" + filenameSuffix + ".csv"

        val inputStream = ProcessorDatasetPipeline.passiveDownload(runSpec, runDir, outputSpec, reportWorkPool)
        return ExecutionDownloadResult(
            inputStream,
            filename,
            mimeTypeCsv
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun delete(
        runDir: Path
    ): ExecutionResult {
        return try {
            reportWorkPool.deleteDir(runDir)
            ExecutionSuccess.empty
        }
        catch (e: Exception) {
            ExecutionFailure(e.message ?: "error")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun startReport(
        reportRunSpec: ReportRunSpec,
        runDir: Path,
        taskHandle: TaskHandle
    ): ProcessorDatasetPipeline {
        logger.info("Starting: $runDir | $reportRunSpec")

        val outputValue = ExecutionValue.of(runDir.toString())

        taskHandle.update(ExecutionSuccess.ofValue(outputValue))

//        val reportHandle = ReportPipeline(
//            reportRunSpec, runDir, taskHandle, reportWorkPool)
        val reportHandle = ProcessorDatasetPipeline(
            reportRunSpec, runDir, reportWorkPool, taskHandle)

        Thread {
            processSync(
                taskHandle, reportHandle, runDir)
        }.start()

        logger.info("Started: {} | {}", runDir, reportRunSpec)

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
        catch (e: Exception) {
            logger.warn("Data processing failed", e)

            reportWorkPool.updateRunStatus(runDir, OutputStatus.Failed)

            taskHandle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
        }
    }
}