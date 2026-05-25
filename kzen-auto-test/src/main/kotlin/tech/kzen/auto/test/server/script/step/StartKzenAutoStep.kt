package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.KzenAutoProcess
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Paths


@Reflect
class StartKzenAutoStep(
    private val name: String,
    private val fixture: String,
    private val port: Int,
    selfLocation: ObjectLocation
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

        val fixturePath = Paths.get(fixture).toAbsolutePath()
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
