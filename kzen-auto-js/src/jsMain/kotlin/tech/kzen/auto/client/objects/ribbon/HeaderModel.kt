package tech.kzen.auto.client.objects.ribbon

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation


// Projected slice of GraphStructure consumed by HeaderController. Reference identity is preserved
// across attribute-only Notation mutations so RPureComponent's default shallow SCU can bail.
data class HeaderModel(
    val archetypeNameByDocument: Map<DocumentPath, ObjectName>
) {
    //-----------------------------------------------------------------------------------------------------------------
    class Builder {
        private var current: HeaderModel? = null


        fun update(graphStructure: GraphStructure): HeaderModel {
            val next = buildFrom(graphStructure.graphNotation)
            val prev = current
            return if (prev != null && prev == next) {
                prev
            }
            else {
                current = next
                next
            }
        }


        private fun buildFrom(notation: GraphNotation): HeaderModel {
            val map = mutableMapOf<DocumentPath, ObjectName>()
            for (documentPath in notation.documents.map.keys) {
                val archetypeName = DocumentArchetype.archetypeName(notation, documentPath)
                    ?: continue
                map[documentPath] = archetypeName
            }
            return HeaderModel(map)
        }
    }
}
