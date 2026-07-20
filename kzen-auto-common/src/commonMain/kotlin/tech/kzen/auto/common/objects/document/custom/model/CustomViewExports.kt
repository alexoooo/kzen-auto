package tech.kzen.auto.common.objects.document.custom.model

import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
data class CustomViewExportsState(
    val entries: List<ScalarAttributeNotation>,
    val membership: Map<ObjectLocation, ScalarAttributeNotation>
)


//---------------------------------------------------------------------------------------------------------------------
object CustomViewExports {
    fun current(
        serverNotation: DocumentObjectNotation,
        graphStructure: GraphStructure,
        mainObjectLocation: ObjectLocation
    ): CustomViewExportsState {
        val mainNotation = serverNotation.notations[NotationConventions.mainObjectPath]
        val exportsListAttribute = mainNotation?.get(CustomConventions.exportsListAttributeName) as? ListAttributeNotation
        val entries = exportsListAttribute?.values.orEmpty().filterIsInstance<ScalarAttributeNotation>()

        val mainReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation)
        val membership = entries.associateBy { entry ->
            graphStructure.graphNotation.coalesce.locate(ObjectReference.parse(entry.value), mainReferenceHost)
        }

        return CustomViewExportsState(entries, membership)
    }
}
