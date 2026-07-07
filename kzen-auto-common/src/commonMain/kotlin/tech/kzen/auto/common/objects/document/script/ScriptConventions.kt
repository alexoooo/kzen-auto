package tech.kzen.auto.common.objects.document.script

import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object ScriptConventions {
    val scriptValidatorLocation = ObjectLocation.parse(
        "auto-jvm/script/script-jvm.yaml#ScriptValidator")

    val objectName = ObjectName("Script")
    val stepObjectName = ObjectName("ScriptStep")
    val runStepObjectName = ObjectName("RunStep")

    val stepsAttributeName = AttributeName("steps")
    val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

    // Branches that hold value bindings (named typed values) rather than executed body steps:
    // the Script's `parameters`, and a ForEachStep's per-iteration `item`. Bindings live here so they
    // are addressable/validated like steps but are rendered outside the body and never executed.
    val parametersAttributeName = AttributeName("parameters")
    val parametersAttributePath = AttributePath.ofName(parametersAttributeName)

    val itemAttributeName = AttributeName("item")
    val itemAttributePath = AttributePath.ofName(itemAttributeName)

    // A ForEachStep's collection reference (the values it iterates over) — distinct from `item`, the
    // branch that holds the per-iteration ForEachItemBinding.
    val itemsAttributeName = AttributeName("items")
    val itemsAttributePath = AttributePath.ofName(itemsAttributeName)

    // The Script's result signature: a `results` map (component name -> TypeMetadata) parsed by
    // ResultSignatureDefiner into the output TupleDefinition. Not a live step object (unlike parameters) —
    // it is plain data on the main Script object declaring what the Script returns; empty/absent => void.
    val resultsAttributeName = AttributeName("results")
    val resultsAttributePath = AttributePath.ofName(resultsAttributeName)

    val instructionsAttributeName = AttributeName("instructions")
    val instructionsAttributePath = AttributePath.ofName(instructionsAttributeName)

    val nextStepTracePath = LogicTracePath(listOf("next-step"))


    // The steps of a branch in document order: the objects nested directly under attributeLocation's
    // object at its attribute (e.g. main.steps, an IfStep's then/else, a ForEachStep's steps). Order is
    // the document position of the step objects (there is no step-list attribute). Mirrors
    // NestedListAttributeDefiner, which feeds the same list to the executor.
    fun orderedDirectChildLocations(
        graphNotation: GraphNotation,
        attributeLocation: AttributeLocation
    ): List<ObjectLocation> {
        val containingLocation = attributeLocation.objectLocation
        val documentNotation = graphNotation.documents[containingLocation.documentPath]
            ?: return listOf()
        return documentNotation
            .directNestedObjectPaths(
                containingLocation.objectPath, attributeLocation.attributePath.attribute)
            .map { ObjectLocation(containingLocation.documentPath, it) }
    }


    fun isScript(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }
}