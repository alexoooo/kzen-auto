package tech.kzen.auto.test.server.sequence.step

import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
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
    TracingSequenceStep(selfLocation)
{
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.empty
    }


    override fun continueOrStart(
        sequenceExecutionContext: SequenceExecutionContext
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
            sequenceExecutionContext,
            "started '$name' on port $port (cwd=$tempDir)")

        return LogicResultSuccess(TupleValue.empty)
    }
}
