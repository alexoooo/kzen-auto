package tech.kzen.auto.server.objects.sequence

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.objects.sequence.step.MultiSequenceStep
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.LogicDefinition
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SequenceDocument(
    steps: List<ObjectLocation>,
//    private val root: ObjectLocation,
    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    Logic,
    SequenceStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        override fun newDocument(
//            archetypeLocation: ObjectLocation
//        ): DocumentObjectNotation {
//            val mainObjectNotation = ObjectNotation.ofParent(archetypeLocation.objectPath.name)
//
//            val objectNotations = ObjectPathMap(
//                persistentMapOf(
//                NotationConventions.mainObjectPath to mainObjectNotation)
//            )
//
//            return DocumentObjectNotation(objectNotations)
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        return LogicDefinition(
            TupleDefinition.empty,
            TupleDefinition.empty)
    }


    override fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        val sequenceExecution = SequenceExecution(
            selfLocation.documentPath, selfLocation,
            logicHandle, logicTraceHandle, logicRunExecutionId)
        sequenceExecution.init(logicControl)
        return sequenceExecution
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val sequenceStepDelegate = MultiSequenceStep(steps)


    override fun valueDefinition(): TupleDefinition {
        return sequenceStepDelegate.valueDefinition()
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        return sequenceStepDelegate.continueOrStart(stepContext)
    }
}