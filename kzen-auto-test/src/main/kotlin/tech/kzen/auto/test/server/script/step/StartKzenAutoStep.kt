package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.KzenAutoProcess
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.nio.file.Paths


@Reflect
class StartKzenAutoStep(
    private val name: String,
    private val fixture: String,
    private val port: Int,
    selfLocation: ObjectLocation,
    @Service private val config: KzenAutoConfig
):
    TracingScriptStep(selfLocation)
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
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

        traceDetail(
            scriptExecutionContext,
            "started '$name' on port $port (cwd=$tempDir)")

        return LogicResultSuccess(TupleValue.empty)
    }
}
