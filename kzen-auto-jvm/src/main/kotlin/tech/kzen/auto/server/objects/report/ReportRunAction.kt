package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.locate.ObjectLocation
import java.nio.file.Path


// TODO: https://stackoverflow.com/questions/3335969/reading-a-gzip-file-from-a-filechannel-java-nio
// https://javadoc.io/static/com.conversantmedia/disruptor/1.2.9/com/conversantmedia/util/concurrent/PushPullConcurrentQueue.html
// http://lmax-exchange.github.io/disruptor/user-guide/index.html
object ReportRunAction
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ReportRunAction::class.java)
    private val mimeTypeCsv = "text/csv"


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
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ReportHandle }

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
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ReportHandle }

        val outputInfo =
            activeReportHandle?.outputPreview(runSpec, outputSpec)
                ?: ReportHandle.passivePreview(runSpec, runDir, outputSpec)

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
        val outPath = ReportHandle.passiveSave(runSpec, runDir, outputSpec)

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
        outputSpec: OutputSpec
    ): ExecutionDownloadResult {
        val inputStream = ReportHandle.passiveDownload(runSpec, runDir, outputSpec)
        return ExecutionDownloadResult(
            inputStream,
            "report.csv",
            mimeTypeCsv
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun delete(
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
    suspend fun startReport(
        reportRunSpec: ReportRunSpec,
        runDir: Path,
        taskHandle: TaskHandle
    ): ReportHandle {
        logger.info("Starting: $runDir | $reportRunSpec")

        val outputValue = ExecutionValue.of(runDir.toString())

        val progress = TaskProgress.ofNotStarted(
            reportRunSpec.inputs.map { it.fileName.toString() })

        taskHandle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        val reportHandle = ReportHandle(
            reportRunSpec, runDir, taskHandle)

        Thread {
            processSync(
                taskHandle, outputValue, reportHandle, runDir)
        }.start()

        logger.info("Done: {} | {}", runDir, reportRunSpec)

        return reportHandle
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        taskHandle: TaskHandle,
        outputValue: ExecutionValue,
        reportHandle: ReportHandle,
        runDir: Path
    ) {
        try {
            reportHandle.run()

            if (taskHandle.cancelRequested()) {
                ReportWorkPool.updateRunStatus(runDir, OutputStatus.Cancelled)
            }
            else {
                ReportWorkPool.updateRunStatus(runDir, OutputStatus.Done)
            }

            taskHandle.complete(
                ExecutionSuccess.ofValue(
                    outputValue))
        }
        catch (e: Exception) {
            logger.warn("Data processing failed", e)

            ReportWorkPool.updateRunStatus(runDir, OutputStatus.Failed)

            taskHandle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
        }
    }
}