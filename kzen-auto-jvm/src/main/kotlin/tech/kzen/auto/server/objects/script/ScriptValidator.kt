package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.script.model.StepValidation
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.registry.ObjectRegistryDocument
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ScriptValidator: DetachedAction {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun validate(
            documentPath: DocumentPath,
            graphNotation: GraphNotation,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance
        ): ScriptValidation {
            val documentNotation = graphNotation.documents[documentPath]
                ?: throw IllegalArgumentException("Document not found: $documentPath")

            val objectRegistryScan = ObjectRegistryDocument.scan(graphNotation)

            val scriptTree = ScriptTree.read(documentPath, graphDefinition)
            val stepValidationBuffer = mutableMapOf<ObjectPath, StepValidation>()
            val scriptDefinitionContext = ScriptDefinitionContext(
                scriptTree,
                ScriptValidation(stepValidationBuffer),
                objectRegistryScan)

            val stepObjectLocations = documentNotation
                .objects
                .notations
                .map
                .keys
                .map { documentPath.toObjectLocation(it) }
                .filter { objectLocation ->
                    graphNotation
                        .inheritanceChain(objectLocation)
                        .any { it.objectPath.name == ScriptConventions.stepObjectName }
                }

            val remainingSteps = mutableSetOf<ObjectPath>()
            remainingSteps.addAll(stepObjectLocations.map { it.objectPath })

            while (remainingSteps.isNotEmpty()) {
                val nextValidations = validationIteration(
                    remainingSteps, scriptDefinitionContext, graphInstance, documentPath)

                if (nextValidations.isEmpty()) {
                    break
                }

                stepValidationBuffer.putAll(nextValidations)

                remainingSteps.removeAll(nextValidations.keys)
            }

            return ScriptValidation(stepValidationBuffer)
        }


        private fun validationIteration(
            remainingObjectPaths: Collection<ObjectPath>,
            scriptDefinitionContext: ScriptDefinitionContext,
            graphInstance: GraphInstance,
            documentPath: DocumentPath
        ):
            Map<ObjectPath, StepValidation>
        {
            val builder = mutableMapOf<ObjectPath, StepValidation>()
            for (objectPath in remainingObjectPaths) {
                val stepObjectLocation = documentPath.toObjectLocation(objectPath)
                val instance = graphInstance.objectInstances[stepObjectLocation]?.reference as? ScriptStep
                if (instance == null) {
                    builder[objectPath] = StepValidation(
                        null, "Not found")
                }
                else {
                    val valueDefinition = instance.definition(scriptDefinitionContext)
                        ?: continue

                    val returnValueDefinition = valueDefinition.returnValueDefinition
                    val typeMetadata =
                        if (returnValueDefinition == null) {
                            null
                        }
                        else {
                            returnValueDefinition
                                .find(TupleComponentName.main)
                                ?.metadata
                                ?: TypeMetadata.unit
                        }

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
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = KzenAutoContext.global().graphCreator
            .createGraph(stepGraphDefinition)

        val scriptValidation = validate(
            documentPath,
            graphDefinitionAttempt.graphStructure.graphNotation,
            stepGraphDefinition,
            graphInstance)

        return ExecutionSuccess.ofValue(scriptValidation.asExecutionValue())
    }
}