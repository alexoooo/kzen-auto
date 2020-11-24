package tech.kzen.auto.server.objects.process

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.OutputSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
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
    suspend fun lookupOutput(
        objectLocation: ObjectLocation,
        runSpec: ProcessRunSpec,
        runDir: Path,
        outputSpec: OutputSpec
    ): ExecutionResult {
        val activeReportHandle = ServerContext
            .modelTaskRepository
            .lookupActive(objectLocation)
            .singleOrNull()
            ?.let { ServerContext.modelTaskRepository.queryRun(it) as? ReportHandle }

        val outputInfo =
            activeReportHandle?.preview(runSpec, outputSpec)
                ?: ReportHandle.passivePreview(runSpec, runDir, outputSpec)

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(outputInfo.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun saveOutput(
        runSpec: ProcessRunSpec,
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
    suspend fun applyProcessAsync(
        runSpec: ProcessRunSpec,
        runDir: Path,
        handle: TaskHandle
    ): ReportHandle {
        logger.info("Starting: $runDir | $runSpec")

        // TODO: value summary and output preview
        val outputValue = ExecutionValue.of(runDir.toString())

        val progress = TaskProgress.ofNotStarted(
            runSpec.inputs.map { it.fileName.toString() })

        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        val reportHandle = ReportHandle(
            runSpec, runDir, handle)

        val reportProgressListener = ReportProgressListener(
            handle, progress)

        Thread {
            processSync(
                runSpec, handle, reportProgressListener, outputValue, reportHandle)
        }.start()

        logger.info("Done: {} | {}", runDir, runSpec)

        return reportHandle
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        runSignature: ProcessRunSpec,
        handle: TaskHandle,
        reportProgressListener: ReportProgressListener,
        outputValue: ExecutionValue,
        reportHandle: ReportHandle
    ) {
        try {
            reportHandle.run(runSignature, reportProgressListener)
        }
        catch (e: Exception) {
            logger.warn("Data processing failed", e)
            handle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
            return
        }

        handle.complete(
            ExecutionSuccess.ofValue(
                outputValue))
    }
}