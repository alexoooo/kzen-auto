package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


/**
 * Hand-written reflection registration for [RecordingSinkWorker] — the test-source equivalent of the
 * KSP-generated `KzenAutoJvmModule` (the test source set has no KSP pass). Idempotently [register]ed into
 * [ReflectionRegistry.global] by [tech.kzen.auto.server.exec.job.JobRunWorkerTest] before it builds its graph.
 */
object RunWorkerTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.objects.job.worker.test.RecordingSinkWorker",
            listOf("input", "selfLocation")
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            RecordingSinkWorker(args[0] as ChannelInput<Any?>, args[1] as ObjectLocation)
        }
    }
}
