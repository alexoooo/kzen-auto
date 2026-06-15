package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.StatefulLogicElement
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PauseStep:
    ScriptStep,
    StatefulLogicElement<PauseStep>
{
    //-----------------------------------------------------------------------------------------------------------------
    private var resumed = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadState(previous: PauseStep) {
        resumed = previous.resumed
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        if (!resumed) {
            resumed = true
            return LogicResultPaused
        }

        // Reset so a re-run (e.g. inside a Loop body) pauses again on the next iteration.
        resumed = false
        return LogicResultSuccess(TupleValue.empty)
    }
}
