package tech.kzen.auto.common.objects.document.custom

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.objects.document.custom.create.CustomCreation
import tech.kzen.auto.common.objects.document.custom.create.CustomCreationSpec
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.metadata.tag.ObjectTag
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object CustomConventions {
    val customDocumentObjectName = ObjectName("CustomDocument")

    val logicTag = ObjectTag("logic")
    val detachedTag = ObjectTag("detached")
    val taskTag = ObjectTag("task")

    val exportsListAttributeName = AttributeName("exports")
    val exportsListAttributePath = AttributePath.ofName(exportsListAttributeName)

    val objectsAttributeName = AttributeName("objects")
    val objectsAttributePath = AttributePath.ofName(objectsAttributeName)


    fun isManaged(attributeName: AttributeName): Boolean {
        return AutoConventions.isManaged(attributeName) ||
            attributeName == exportsListAttributeName ||
            attributeName == objectsAttributeName
    }


    fun isCustomDocument(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == customDocumentObjectName.value
    }


    fun listPrototypes(graphStructure: GraphStructure): List<CustomCreation> {
        val graphNotation = graphStructure.graphNotation
        val marker = graphNotation.coalesce.locateOptional(
            ObjectReference.ofRootName(CustomCreation.customCreatableObjectName))
            ?: return emptyList()

        return graphNotation.objectLocations.mapNotNull { location ->
            val isAbstract = graphNotation
                .directAttribute(location, NotationConventions.abstractAttributePath)
                ?.asString() == "true"
            if (!isAbstract) {
                return@mapNotNull null
            }

            val chain = graphNotation.inheritanceChain(location)
            val markerIndex = chain.indexOf(marker)
            if (markerIndex < 1) {
                return@mapNotNull null
            }
            val contributionChain = chain.take(markerIndex)
            if (contributionChain.none {
                    graphNotation.directAttribute(it, NotationConventions.classAttributePath) != null
                }) {
                return@mapNotNull null
            }

            if (graphStructure.graphMetadata.objectMetadata[location]
                    ?.attributes?.map?.get(CustomCreation.customCreateAttributeName) == null) {
                return@mapNotNull null
            }
            val customCreateNotation = graphNotation.firstAttribute(
                location, CustomCreation.customCreateAttributeName) as? MapAttributeNotation
                ?: return@mapNotNull null
            val spec = CustomCreationSpec.ofNotation(customCreateNotation)
            val body = creationBody(location, spec, graphNotation)
            CustomCreation(
                location,
                spec.category,
                spec.label.ifBlank { location.objectPath.name.value },
                body)
        }.sortedWith(compareBy<CustomCreation>(
            { it.category },
            { it.label },
            { it.prototype.asString() }))
    }


    private fun creationBody(
        prototype: ObjectLocation,
        spec: CustomCreationSpec,
        graphNotation: GraphNotation
    ): ObjectNotation {
        var body = ObjectNotation.ofParent(prototype.toReference())
        for (attributeName in spec.defaults) {
            val value = graphNotation.firstAttribute(prototype, attributeName)
            body = body.upsertAttribute(attributeName, value)
        }
        return body
    }


    fun customDocumentExports(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        documentNotation: DocumentNotation
    ): List<ObjectLocation> {
        val mainNotation = documentNotation.objects.notations[NotationConventions.mainObjectPath]!!

        // A custom document may declare no `exports` list (e.g. the bundled main/Custom.yaml) — it then
        // exports nothing. Guard the cast: the raw notation carries no metadata default, so an absent
        // attribute is null here, and `null as ListAttributeNotation` throws (a ClassCastException in JS).
        val exportsAttribute = mainNotation.get(exportsListAttributeName) as? ListAttributeNotation
            ?: return listOf()
        val host = ObjectReferenceHost.ofLocation(
            ObjectLocation(documentPath, NotationConventions.mainObjectPath))
        return exportsAttribute.values.map { entry ->
            val ref = ObjectReference.parse((entry as ScalarAttributeNotation).value)
            graphNotation.coalesce.locate(ref, host)
        }
    }


    fun customDocumentExportedLogic(
        graphNotation: GraphNotation,
        graphMetadata: GraphMetadata,
        documentPath: DocumentPath,
        documentNotation: DocumentNotation
    ): List<ObjectLocation> {
        return customDocumentExports(graphNotation, documentPath, documentNotation)
            .filter { graphMetadata.objectMetadata[it]?.tags?.contains(logicTag) == true }
    }
}
