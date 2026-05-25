package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class StopKzenAutoStep(
    private val name: String,
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
        val entry = KzenAutoSubprocessRegistry.remove(name)
        if (entry == null) {
            traceDetail(
                scriptExecutionContext,
                "no SUT registered as '$name', nothing to stop")
            return LogicResultSuccess(TupleValue.empty)
        }

        entry.process.close()
        entry.tempDir?.let { FixtureCopier.deleteRecursively(it) }

        traceDetail(scriptExecutionContext, "stopped '$name'")
        return LogicResultSuccess(TupleValue.empty)
    }
}
