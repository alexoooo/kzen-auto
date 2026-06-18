package tech.kzen.auto.server.objects.script.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.StatefulLogicElement
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils


@Reflect
class IfStep(
    private val condition: ObjectLocation,
    then: List<ObjectLocation>,
    `else`: List<ObjectLocation>
):
    ScriptStep,
    StatefulLogicElement<IfStep>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(IfStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private enum class State {
        Initial,
        ThenBranch,
        ElseBranch
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val thenDelegate = MultiStep(then)
    private val elseDelegate = MultiStep(`else`)

    private var state = State.Initial


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadState(previous: IfStep) {
        state = previous.state
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.empty)
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        if (state == State.Initial) {
            val conditionValue = scriptExecutionContext.referencedValue(condition)
            check(conditionValue is Boolean) {
                "Boolean expected: $condition = $conditionValue"
            }

            state =
                if (conditionValue) {
                    State.ThenBranch
                }
                else {
                    State.ElseBranch
                }
        }

        val step =
            if (state == State.ThenBranch) {
                thenDelegate
            }
            else {
                elseDelegate
            }

        val result =
            try {
                step.continueOrStart(scriptExecutionContext)
            }
            catch (t: Throwable) {
                logger.warn("Branch error - {}", step, t)
                LogicResultFailed(ExceptionUtils.message(t))
            }

        if (result.isTerminal()) {
            state = State.Initial
        }

        return result
    }
}