package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Test-only sink Worker for [tech.kzen.auto.server.exec.job.JobScratchDirTest]: obtains its private scratch
 * directory via [JobControl.scratchDir] in [onStart] (recording it in [ScratchProbeLog]), then drains its input
 * without consuming it meaningfully. Its dir must be DISTINCT from its upstream [ScratchProbeSourceWorker]'s (a
 * different Worker stable id) yet a sibling under the same run dir — the isolation the test asserts.
 *
 * Registered via [ScratchWorkerTestModule] (no `@Reflect` / KSP in the test source set).
 */
class ScratchProbeSinkWorker(
    input: ChannelInput<Any?>,
    selfLocation: ObjectLocation
):
    SinkWorker<Any?>(input, selfLocation)
{
    private val workerName = selfLocation.objectPath.name.value


    override suspend fun onStart(control: JobControl) {
        ScratchProbeLog.probe(control, workerName)
    }


    override suspend fun onElement(element: Any?, control: JobControl) {}
}
