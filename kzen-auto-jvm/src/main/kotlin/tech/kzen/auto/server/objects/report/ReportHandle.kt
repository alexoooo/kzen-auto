package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.ReportInput
import tech.kzen.auto.server.objects.report.pipeline.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import java.nio.file.Path


class ReportHandle(
    initialReportRunSpec: ReportRunSpec,
    runDir: Path,
    taskHandle: TaskHandle?
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportHandle::class.java)

//        private const val progressItems = 1_000
        private const val progressItems = 10_000
//        private const val progressItems = 250_000


        fun passivePreview(reportRunSpec: ReportRunSpec, runDir: Path, outputSpec: OutputSpec): OutputInfo {
            return ofPassive(reportRunSpec, runDir).use {
                it.outputPreview(reportRunSpec, outputSpec)
            }
        }


        fun passiveSave(reportRunSpec: ReportRunSpec, runDir: Path, outputSpec: OutputSpec): Path? {
            return ofPassive(reportRunSpec, runDir).use {
                it.outputSave(reportRunSpec, outputSpec)
            }
        }


        private fun ofPassive(reportRunSpec: ReportRunSpec, runDir: Path): ReportHandle {
            return ReportHandle(reportRunSpec, runDir, null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val input = ReportInput(initialReportRunSpec, taskHandle)
    private val filter = ReportFilter(initialReportRunSpec)
    private val summary = ReportSummary(initialReportRunSpec, runDir, taskHandle)
    private val output = ReportOutput(initialReportRunSpec, runDir, taskHandle)


    //-----------------------------------------------------------------------------------------------------------------
    fun outputPreview(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): OutputInfo {
        return output.preview(reportRunSpec, outputSpec)
    }


    private fun outputSave(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): Path? {
        return output.save(reportRunSpec, outputSpec)
    }


    fun summaryView(): TableSummary {
        return summary.view()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun run() {
        while (true) {
            val moreRemaining = input.poll { record ->
                if (filter.test(record)) {
                    summary.add(record)
                    output.add(record)
                }
            }

            if (! moreRemaining) {
                break
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        input.close()
        summary.close()
        output.close()
    }
}