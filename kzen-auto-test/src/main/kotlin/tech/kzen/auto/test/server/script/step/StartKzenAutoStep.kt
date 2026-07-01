package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.KzenAutoProcess
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.nio.file.Paths


@Reflect
class StartKzenAutoStep(
    private val name: String,
    private val fixture: String,
    private val port: Int,
    private val closePolicy: ResourceClosePolicy,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service private val config: KzenAutoConfig
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val jarPath = System.getProperty("kzenAutoJar")
            ?: error(
                "System property 'kzenAutoJar' not set; the tester JVM needs -DkzenAutoJar=<path-to-kzen-auto-jvm-fat-jar>. " +
                "The selfTest Gradle task and the Tester IDE run config both set this for you.")

        // Relative fixture paths are module assets — resolve against the module root the tester
        //  was launched with (cwd-independent); absolute paths pass through resolve() unchanged.
        val fixturePath = (config.moduleRoot?.resolve(fixture) ?: Paths.get(fixture))
            .toAbsolutePath().normalize()
        val tempDir = FixtureCopier.copyToTemp(fixturePath, "kzen-sut-$name-")

        val process = KzenAutoProcess.startFromJar(
            name = name,
            jar = Paths.get(jarPath),
            cwd = tempDir,
            port = port)

        KzenAutoSubprocessRegistry.put(name, process, tempDir)

        // Register the SUT as a run-scoped resource: the engine auto-disposes it (killing the process and
        //  deleting its temp dir via the registry) when the run settles, per closePolicy. A matching Stop
        //  step tears it down eagerly and releaseResource-s the key, so this closer does not double-fire.
        execution.openResource(KzenAutoSubprocessRegistry.resourceKey(name), name, closePolicy) {
            KzenAutoSubprocessRegistry.removeAndClose(name)
        }

        execution.traceDetail("started '$name' on port $port (cwd=$tempDir)")

        return null
    }
}
