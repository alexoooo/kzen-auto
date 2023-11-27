package tech.kzen.auto.server.objects.sequence

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation
import tech.kzen.auto.common.objects.document.sequence.model.StepValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SequenceValidator: DetachedAction {
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val documentPathValue = request.getSingle(CommonRestApi.paramHostDocumentPath)
            ?: return ExecutionResult.failure("Missing document path")

        val documentPath = DocumentPath.parse(documentPathValue)
        val graphDefinitionAttempt = KzenAutoContext.global().graphStore.graphDefinition()
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

        val documentNotation = graphNotation.documents[documentPath]
            ?: return ExecutionResult.failure("Document not found: $documentPath")

        val stepObjectLocations = documentNotation
            .objects
            .notations
            .values
            .keys
            .map { documentPath.toObjectLocation(it) }
            .filter { objectLocation ->
                graphNotation
                    .inheritanceChain(objectLocation)
                    .any { it.objectPath.name == SequenceConventions.stepObjectName }
            }

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful()
            .filterTransitive(stepObjectLocations)

        val objectGraph = KzenAutoContext.global().graphCreator
            .createGraph(stepGraphDefinition)

        val stepValidations = mutableMapOf<ObjectLocation, StepValidation>()

        for (stepObjectLocation in stepObjectLocations) {
            val instance = objectGraph.objectInstances[stepObjectLocation]?.reference as? SequenceStep
            if (instance == null) {
                stepValidations[stepObjectLocation] =
                    StepValidation(null, "Not found")
            }
            else {
                val valueDefinition = instance.definition()

                val typeMetadata = valueDefinition.returnValueDefinition?.find(TupleComponentName.main)?.metadata

                stepValidations[stepObjectLocation] =
                    StepValidation(typeMetadata, valueDefinition.validationError)
            }
        }

        val sequenceValidation = SequenceValidation(stepValidations)

        return ExecutionSuccess.ofValue(sequenceValidation.asExecutionValue())
    }
}