package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


/**
 * Hand-written reflection registration for the test-only Flow vertex [CountingSinkVertex] — the test-source
 * equivalent of the KSP-generated `KzenAutoJvmModule` entry for [tech.kzen.auto.server.objects.flow.vertex.AccumulateSink]
 * (same `listOf("input", ...)` arg-name + factory shape). The test source set has no KSP pass, so the class
 * carries no `@Reflect` annotation and is registered manually instead. The injected `input` is resolved by the
 * flow's channel wiring (`by: FlowWiring` in the fixture's archetype) and handed to the factory as a
 * [RequiredInput], exactly as for the production sinks.
 *
 * Idempotently [register]ed into [ReflectionRegistry.global] by [tech.kzen.auto.server.exec.flow.FlowMigrationTest]
 * before it builds its graph.
 */
object FlowVertexTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.flow.test.CountingSinkVertex",
            listOf("input", "note")
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            CountingSinkVertex(args[0] as RequiredInput<Any>, args[1] as String)
        }
    }
}
