package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
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


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun startReport(
        runSpec: ReportRunSpec,
        runDir: Path,
        taskHandle: TaskHandle
    ): ReportHandle {
        logger.info("Starting: $runDir | $runSpec")

        val outputValue = ExecutionValue.of(runDir.toString())

        val progress = TaskProgress.ofNotStarted(
            runSpec.inputs.map { it.fileName.toString() })

        taskHandle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        val reportHandle = ReportHandle(
            runSpec, runDir, taskHandle)

        Thread {
            processSync(
                taskHandle, outputValue, reportHandle)
        }.start()

        logger.info("Done: {} | {}", runDir, runSpec)

        return reportHandle
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        taskHandle: TaskHandle,
        outputValue: ExecutionValue,
        reportHandle: ReportHandle
    ) {
        try {
            reportHandle.run()
        }
        catch (e: Exception) {
            logger.warn("Data processing failed", e)
            taskHandle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
            return
        }
//        finally {
//            reportHandle.close()
//        }

        taskHandle.complete(
            ExecutionSuccess.ofValue(
                outputValue))
    }
}