package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


/**
 * Hand-written reflection registration for the test-only Job Workers ([GatedSourceWorker] /
 * [GatedCountingSinkWorker]) — the test-source equivalent of the KSP-generated `KzenAutoJvmModule`. The test
 * source set has no KSP pass (the build declares only the main `ksp(...)` dependency), so these classes carry
 * no `@Reflect` annotation and are registered manually instead, mirroring exactly what the processor would
 * emit: constructor argument names + a factory that maps resolved args onto the constructor.
 *
 * Idempotently [register]ed into [ReflectionRegistry.global] (the same registry the graph creator reads) by the
 * carryover test before it builds its graph.
 */
object GatedWorkerTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker",
            listOf("output", "total", "selfLocation")
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            GatedSourceWorker(args[0] as ChannelOutput<Any?>, args[1] as Int, args[2] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker",
            listOf("input", "note", "selfLocation")
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            GatedCountingSinkWorker(args[0] as ChannelInput<Any?>, args[1] as String, args[2] as ObjectLocation)
        }
    }
}
