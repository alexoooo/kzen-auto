package tech.kzen.auto.server.objects.sequence.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.StatefulLogicElement
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils


@Reflect
class IfStep(
    private val condition: ObjectLocation,
    then: List<ObjectLocation>,
    `else`: List<ObjectLocation>
):
    SequenceStep,
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
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.of(
            TupleDefinition.empty)
    }


    override fun continueOrStart(sequenceExecutionContext: SequenceExecutionContext): LogicResult {
        if (state == State.Initial) {
            val conditionStep = sequenceExecutionContext.activeSequenceModel.steps[condition]

            val conditionValue = conditionStep?.value?.mainComponentValue()
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
                step.continueOrStart(sequenceExecutionContext)
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