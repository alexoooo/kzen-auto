package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Test-only source Worker for [tech.kzen.auto.server.exec.job.JobScratchDirTest]: obtains its private scratch
 * directory via [JobControl.scratchDir] (recording it in [ScratchProbeLog]), then emits a single element so its
 * downstream sink runs too. Paired with [ScratchProbeSinkWorker], this puts two distinct Workers in one run so
 * the test can assert their scratch dirs are isolated and both swept when the run settles.
 *
 * Registered via [ScratchWorkerTestModule] (no `@Reflect` / KSP in the test source set).
 */
class ScratchProbeSourceWorker(
    output: ChannelOutput<Any?>,
    selfLocation: ObjectLocation
):
    SourceWorker<Any?>(output, selfLocation)
{
    private val workerName = selfLocation.objectPath.name.value


    override suspend fun produce(emit: Emitter<Any?>, control: JobControl) {
        ScratchProbeLog.probe(control, workerName)
        emit.send("probe")
    }
}
