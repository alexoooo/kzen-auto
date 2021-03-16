package tech.kzen.auto.server.objects.report.pipeline.input

import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.DatasetDefinition
import java.nio.file.Path


class ProcessorDatasetPipeline(
    val datasetDefinition: DatasetDefinition,
    runDir: Path,
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------


    //-----------------------------------------------------------------------------------------------------------------



    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {

    }
}