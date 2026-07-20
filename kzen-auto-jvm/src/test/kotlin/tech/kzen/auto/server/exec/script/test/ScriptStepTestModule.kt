package tech.kzen.auto.server.exec.script.test

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


/**
 * The pinned proof that [ModuleReflection] is a trivially hand-implementable contract — what a third party
 * ships when it registers its own classes without the KSP processor, mirroring exactly what the processor
 * emits: constructor argument names + a factory that maps resolved args onto the constructor. It is the only
 * hand-written module left; every other test fixture is `@Reflect`-annotated and served by the JVM reflective
 * mirror, and the contrast between the two paths is itself the coverage.
 *
 * Registers the test-only Script steps ([ShoutStep], [OpenResourceTestStep], [FailStep], [FlakyStep],
 * [AssertDisposedStep], [ReadResourceStep], [CountingStep], [BinaryDetailStep]), which therefore carry no
 * `@Reflect` annotation. That these third-party steps run end-to-end after only this registration — with no
 * change to [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] or any kzen dispatch — is the extensibility
 * guarantee [tech.kzen.auto.server.exec.script.ScriptExtensibilityTest] asserts. Idempotently [register]ed into
 * [ReflectionRegistry.global] by that test before it builds its graph.
 */
object ScriptStepTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.ShoutStep",
            listOf("input")
        ) { args ->
            ShoutStep(args[0] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.OpenResourceTestStep",
            listOf("key", "closePolicy")
        ) { args ->
            OpenResourceTestStep(args[0] as String, args[1] as String)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.FailStep",
            listOf("message")
        ) { args ->
            FailStep(args[0] as String)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.FlakyStep",
            listOf("input")
        ) { args ->
            FlakyStep(args[0] as ObjectLocation)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.AssertDisposedStep",
            listOf("key", "expectedDisposed")
        ) { args ->
            AssertDisposedStep(args[0] as String, args[1] as Boolean)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.ReadResourceStep",
            listOf("key")
        ) { args ->
            ReadResourceStep(args[0] as String)
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.CountingStep",
            listOf()
        ) {
            CountingStep()
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.exec.script.test.BinaryDetailStep",
            listOf()
        ) {
            BinaryDetailStep()
        }
    }
}
