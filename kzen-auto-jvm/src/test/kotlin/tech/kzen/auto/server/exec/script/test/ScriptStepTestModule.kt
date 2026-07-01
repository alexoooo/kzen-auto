package tech.kzen.auto.server.exec.script.test

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


/**
 * Hand-written reflection registration for the test-only Script steps ([ShoutStep], [OpenResourceTestStep],
 * [FailStep], [FlakyStep]) — the test-source equivalent of the KSP-generated `KzenAutoJvmModule`. The test source set has no
 * KSP pass (the build declares only the main `ksp(...)` dependency), so these classes carry no `@Reflect`
 * annotation and are registered manually instead, mirroring exactly what the processor would emit: constructor
 * argument names + a factory that maps resolved args onto the constructor.
 *
 * That these third-party steps run end-to-end after only this registration — with no change to
 * [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] or any kzen dispatch — is the extensibility guarantee
 * [tech.kzen.auto.server.exec.script.ScriptExtensibilityTest] asserts. Idempotently [register]ed into
 * [ReflectionRegistry.global] by that test before it builds its graph.
 */
object ScriptStepTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.ShoutStep",
            listOf("input", "selfLocation")
        ) { args ->
            ShoutStep(args[0] as ObjectLocation, args[1] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.OpenResourceTestStep",
            listOf("key", "closePolicy", "selfLocation")
        ) { args ->
            OpenResourceTestStep(args[0] as String, args[1] as String, args[2] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.FailStep",
            listOf("message", "selfLocation")
        ) { args ->
            FailStep(args[0] as String, args[1] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.FlakyStep",
            listOf("input", "selfLocation")
        ) { args ->
            FlakyStep(args[0] as ObjectLocation, args[1] as ObjectLocation)
        }
    }
}
