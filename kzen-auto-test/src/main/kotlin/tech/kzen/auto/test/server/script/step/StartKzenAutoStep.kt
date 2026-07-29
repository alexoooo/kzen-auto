package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.FreePort
import tech.kzen.auto.test.server.process.KzenAutoProcess
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.auto.test.server.process.SutHandle
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.nio.file.Paths


@Reflect
class StartKzenAutoStep(
    private val name: String,
    private val fixture: String,
    private val port: Int,
    private val closePolicy: ResourceClosePolicy,
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

        // `port: 0` (the notation default) means "pick a free one", so a SUT never contends with a
        //  developer's own kzen-auto instances or with a concurrent run. Navigate to it with
        //  BrowserGetSutStep, which addresses this SUT by name and reads the resolved port off the
        //  handle below. Pin a literal port only when you want a predictable URL to attach to.
        val resolvedPort = if (port == 0) FreePort.next() else port
        val handle = SutHandle(name, resolvedPort)

        val process = KzenAutoProcess.startFromJar(
            name = name,
            jar = Paths.get(jarPath),
            cwd = tempDir,
            port = resolvedPort)

        KzenAutoSubprocessRegistry.put(name, process, tempDir)

        // Provide the SUT as this step's declared SutContext, qualified by name — the engine key is
        //  "sut:$name", so one `sut` slot owns every named SUT independently. The engine auto-disposes it
        //  (killing the process and deleting its temp dir via the registry) at the OWNING document's settle,
        //  per closePolicy. A matching Stop step tears it down eagerly and releases the key, so this closer
        //  does not double-fire. The registered VALUE is the handle, so a later step can resolve this SUT's
        //  run-time port by name through the engine's ancestor-chain lookup — no YAML repeats the port as a
        //  URL literal.
        execution.provideContext(handle, closePolicy, qualifier = name) {
            KzenAutoSubprocessRegistry.removeAndClose(name)
        }

        execution.traceDetail("started '$name' on ${handle.baseUrl} (cwd=$tempDir)")

        return null
    }
}
