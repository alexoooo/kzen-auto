package tech.kzen.auto.server.objects.process

import com.google.common.base.Stopwatch
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.*
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
import tech.kzen.auto.server.objects.process.pipeline.ReportOutput
import tech.kzen.auto.server.objects.process.stream.RecordStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit


class ReportHandle(
    initialProcessRunSpec: ProcessRunSpec,
    runDir: Path,
    private val handle: TaskHandle?
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportHandle::class.java)

//        private const val progressItems = 1_000
        private const val progressItems = 10_000
//        private const val progressItems = 250_000


        fun passivePreview(processRunSpec: ProcessRunSpec, runDir: Path, outputSpec: OutputSpec): OutputInfo {
            return ofPassive(processRunSpec, runDir).use {
                it.preview(processRunSpec, outputSpec)
            }
        }


        fun passiveSave(processRunSpec: ProcessRunSpec, runDir: Path, outputSpec: OutputSpec): Path? {
            return ofPassive(processRunSpec, runDir).use {
                it.save(processRunSpec, outputSpec)
            }
        }


        private fun ofPassive(processRunSpec: ProcessRunSpec, runDir: Path): ReportHandle {
            return ReportHandle(processRunSpec, runDir, null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val output = ReportOutput(initialProcessRunSpec, runDir, handle)


    //-----------------------------------------------------------------------------------------------------------------
    fun preview(processRunSpec: ProcessRunSpec, outputSpec: OutputSpec): OutputInfo {
        return output.preview(processRunSpec, outputSpec)
    }


    private fun save(processRunSpec: ProcessRunSpec, outputSpec: OutputSpec): Path? {
        return output.save(processRunSpec, outputSpec)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun summary(processRunSignature: ProcessRunSignature): TableSummary {
        return TableSummary.empty
//        val notLoaded = mutableListOf<Path>()
//
//        val builder = mutableMapOf<String, ColumnSummary>()
//
//        next_input_path@
//        for (inputPath in processRunSignature.inputs) {
//            val indexDir = FilterIndex.inputIndexPath(inputPath)
//            if (! Files.isDirectory(indexDir)) {
//                notLoaded.add(inputPath)
//                continue
//            }
//
//            for (columnName in processRunSignature.columnNames) {
//                val columnDirName = AutoJvmUtils.sanitizeFilename(columnName)
//                val columnDir = indexDir.resolve(columnDirName)
//
//                if (! Files.exists(columnDir)) {
//                    notLoaded.add(inputPath)
//                    continue@next_input_path
//                }
//
//                val columnSummary = loadValueSummary(columnDir)
//                val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
//                builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
//            }
//        }
//
//        if (notLoaded.isEmpty()) {
//            val tableSummary = TableSummary(builder)
//
//            handle.complete(
//                ExecutionSuccess.ofValue(
//                ExecutionValue.of(tableSummary.toCollection())))
//        }
//        else {
//            ColumnSummaryAction.summarizeRemainingAsync(notLoaded, runSignature, builder, handle)
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun run(
        processRunSpec: ProcessRunSpec,
        reportProgressListener: ReportProgressListener
    ) {
        val filterColumns = processRunSpec.columnNames
            .intersect(processRunSpec.filter.columns.keys)
            .toList()

        for (inputPath in processRunSpec.inputs) {
            logger.info("Reading: {}", inputPath)

            FileStreamer.open(inputPath)!!.use { stream ->
                processStream(
                    stream,
                    filterColumns,
                    processRunSpec,
                    reportProgressListener,
                    inputPath
                )
            }
        }
    }


    private fun processStream(
        input: RecordStream,
        filterColumns: List<String>,
        runSignature: ProcessRunSpec,
        reportProgressListener: ReportProgressListener,
        inputPath: Path
    ) {
        check(handle != null)

        reportProgressListener.update(
            inputPath.fileName.toString(),
            "Started processing")

        var count: Long = 0
        var pivotCount: Long = 0
        val outerStopwatch = Stopwatch.createStarted()
        val innerStopwatch = Stopwatch.createStarted()

        next_record@
        while (input.hasNext() && ! handle.cancelRequested()) {
            val record = input.next()

            if (count != 0L && count % progressItems == 0L) {
                val progressMessage =
                    "Processed ${ColumnSummaryAction.formatCount(count)}, " +
                            "filtered ${ColumnSummaryAction.formatCount(pivotCount)} " +
                            "at ${ColumnSummaryAction.formatCount(
                                (1000.0 * progressItems / innerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong())}/s"
                innerStopwatch.reset().start()

                logger.info(progressMessage)

                reportProgressListener.update(
                    inputPath.fileName.toString(),
                    progressMessage)
            }
            count++

            for (filterColumn in filterColumns) {
                val value = record.get(filterColumn)

                @Suppress("MapGetWithNotNullAssertionOperator")
                val columnCriteria = runSignature.filter.columns[filterColumn]!!

                if (columnCriteria.values.isNotEmpty()) {
                    val present = columnCriteria.values.contains(value)

                    val allow =
                        when (columnCriteria.type) {
                            ColumnFilterType.RequireAny ->
                                present

                            ColumnFilterType.ExcludeAll ->
                                ! present
                        }

                    if (! allow) {
                        continue@next_record
                    }
                }
            }

            pivotCount++
            output.add(record)
        }

        val speedMessage =
            "at ${ColumnSummaryAction.formatCount(
                (1000.0 * count / outerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong())}/s overall"
        if (handle.cancelRequested()) {
            val message =
                "Interrupted: " + ColumnSummaryAction.formatCount(count) +
                        ", wrote " + ColumnSummaryAction.formatCount(pivotCount) +
                        " $speedMessage"
            reportProgressListener.update(inputPath.fileName.toString(), message)
            logger.info(message)
        }
        else {
            val message =
                "Finished " + ColumnSummaryAction.formatCount(count) +
                        ", wrote " + ColumnSummaryAction.formatCount(pivotCount) +
                        " $speedMessage"
            reportProgressListener.update(inputPath.fileName.toString(), message)
            logger.info(message)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        output.close()
    }
}