package tech.kzen.auto.common.objects.document.custom

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.metadata.tag.ObjectTag
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object CustomConventions {
    private val customDocumentObjectName = ObjectName("CustomDocument")

    val prototypeObjectName: ObjectName = ObjectName("Prototype")

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


    fun listPrototypes(graphNotation: GraphNotation): List<ObjectLocation> {
        return graphNotation.objectLocations.filter { location ->
            val isAttribute = graphNotation
                .directAttribute(location, NotationConventions.isAttributePath)
                ?.asString()
            isAttribute == prototypeObjectName.value
        }
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
