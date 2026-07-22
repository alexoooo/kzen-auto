package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobMessage
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only source Worker for [tech.kzen.auto.server.exec.job.JobScratchDirTest]: obtains its private scratch
 * directory via [JobControl.scratchDir] (recording it in [ScratchProbeLog]), then emits a single element so its
 * downstream sink runs too. Paired with [ScratchProbeSinkWorker], this puts two distinct Workers in one run so
 * the test can assert their scratch dirs are isolated and both swept when the run settles.
 *
 * `@Reflect` with no KSP pass over the test source set: the graph instantiates it through the JVM reflective
 * mirror rather than a generated registration.
 */
@Reflect
class ScratchProbeSourceWorker(
    output: ChannelOutput<Any?>,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    private val workerName = selfLocation.objectPath.name.value


    override suspend fun produce(emit: Emitter, control: JobControl) {
        ScratchProbeLog.probe(control, workerName)
        emit.send(JobMessage.ofPayload("probe"))
    }
}
