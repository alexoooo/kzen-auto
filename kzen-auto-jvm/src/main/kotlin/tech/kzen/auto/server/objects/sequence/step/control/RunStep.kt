package tech.kzen.auto.server.objects.sequence.step.control

import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.LogicExecutionFacade
import tech.kzen.auto.server.service.v1.StatefulLogicElement
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentValue
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class RunStep(
    private val instructions: ObjectLocation,
    private val arguments: Map<String, ObjectLocation>,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation),
    StatefulLogicElement<RunStep>
{
//    companion object {
//        private val logger = LoggerFactory.getLogger(InvokeSequenceStep::class.java)
//    }


    private var pausedExecution: LogicExecutionFacade? = null


    override fun loadState(previous: RunStep) {
        pausedExecution = previous.pausedExecution
    }


    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.of(
            TupleDefinition.ofMain(LogicType.any))
    }


    override fun continueOrStart(sequenceExecutionContext: SequenceExecutionContext): LogicResult {
        val command = sequenceExecutionContext.logicControl.pollCommand()
        if (command == LogicCommand.Cancel) {
            pausedExecution?.close()
            pausedExecution = null
            return LogicResultCancelled
        }

        val existing = pausedExecution

        val execution =
            if (existing != null) {
                existing
            }
            else {
                val created = sequenceExecutionContext.logicHandleFacade.start(instructions)

                val argumentTupleComponents = arguments.map {
                    TupleComponentValue(
                        TupleComponentName(it.key),
                        sequenceExecutionContext.activeSequenceModel.steps[it.value]?.value?.mainComponentValue())
                }

                val argumentValue = TupleValue(argumentTupleComponents)

                val initResult = created.beforeStart(argumentValue)
                if (! initResult) {
                    created.close()
                    return LogicResultFailed("Unable to initialize $instructions")
                }

                created
            }

        try {
            val runResult = execution.continueOrStart()

            pausedExecution =
                if (runResult is LogicResultPaused) {
                    execution
                }
                else {
                    if (runResult is LogicResultSuccess) {
                        traceValue(sequenceExecutionContext, runResult.value.mainComponentValue())
                    }

                    execution.close()
                    null
                }

            return runResult
        }
        catch (t: Throwable) {
            t.printStackTrace()
            execution.close()
            pausedExecution = null
            return LogicResultFailed(
                ExecutionFailure.ofException(t).errorMessage)
        }
    }
}