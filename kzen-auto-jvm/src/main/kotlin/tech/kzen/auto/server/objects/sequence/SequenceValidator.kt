package tech.kzen.auto.server.objects.sequence

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation
import tech.kzen.auto.common.objects.document.sequence.model.StepValidation
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SequenceValidator: DetachedAction {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun validate(
            documentPath: DocumentPath,
            graphNotation: GraphNotation,
            graphInstance: GraphInstance
        ): SequenceValidation {
            val documentNotation = graphNotation.documents[documentPath]
                ?: throw IllegalArgumentException("Document not found: $documentPath")

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

            val sequenceTree = SequenceTree.read(documentNotation)
            val stepValidationBuffer = mutableMapOf<ObjectPath, StepValidation>()
            val sequenceDefinitionContext = SequenceDefinitionContext(
                sequenceTree,
                SequenceValidation(stepValidationBuffer))

            val remainingSteps = mutableSetOf<ObjectPath>()
            remainingSteps.addAll(stepObjectLocations.map { it.objectPath })

            while (remainingSteps.isNotEmpty()) {
                val nextValidations = validationIteration(
                    remainingSteps, sequenceDefinitionContext, graphInstance, documentPath)

                if (nextValidations.isEmpty()) {
                    break
                }

                stepValidationBuffer.putAll(nextValidations)

                remainingSteps.removeAll(nextValidations.keys)
            }

            return SequenceValidation(stepValidationBuffer)
        }


        private fun validationIteration(
            remainingObjectPaths: Collection<ObjectPath>,
            sequenceDefinitionContext: SequenceDefinitionContext,
            graphInstance: GraphInstance,
            documentPath: DocumentPath
        ):
            Map<ObjectPath, StepValidation>
        {
            val builder = mutableMapOf<ObjectPath, StepValidation>()
            for (objectPath in remainingObjectPaths) {
                val stepObjectLocation = documentPath.toObjectLocation(objectPath)
                val instance = graphInstance.objectInstances[stepObjectLocation]?.reference as? SequenceStep
                if (instance == null) {
                    builder[objectPath] = StepValidation(
                        null, "Not found")
                }
                else {
                    val valueDefinition = instance.definition(sequenceDefinitionContext)
                        ?: continue

                    val typeMetadata = valueDefinition.returnValueDefinition?.find(TupleComponentName.main)?.metadata

                    builder[objectPath] = StepValidation(
                        typeMetadata, valueDefinition.validationError)
                }
            }
            return builder
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val documentPathValue = request.getSingle(CommonRestApi.paramHostDocumentPath)
            ?: return ExecutionResult.failure("Missing document path")

        val documentPath = DocumentPath.parse(documentPathValue)

        val graphDefinitionAttempt = KzenAutoContext.global().graphStore.graphDefinition()

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful()
            .filterTransitive(documentPath)

        val graphInstance = KzenAutoContext.global().graphCreator
            .createGraph(stepGraphDefinition)

        val sequenceValidation = validate(
            documentPath,
            graphDefinitionAttempt.graphStructure.graphNotation,
            graphInstance)

        return ExecutionSuccess.ofValue(sequenceValidation.asExecutionValue())
    }
}