package tech.kzen.auto.client.objects.sidebar

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


// Projected slice of GraphStructure consumed by the sidebar subtree. Maintained by Builder so
// that reference identity changes only when the projected slice actually changes — attribute-only
// Notation mutations leave the model reference untouched, so RPureComponent's default shallow
// SCU bails on the sidebar without any custom override.
data class SidebarModel(
    val mainDocumentPaths: List<DocumentPath>,
    val existingDocumentPaths: Set<DocumentPath>,
    val archetypes: List<ArchetypeInfo>,
    val archetypeOfDocument: Map<DocumentPath, ArchetypeInfo>
) {
    //-----------------------------------------------------------------------------------------------------------------
    data class ArchetypeInfo(
        val location: ObjectLocation,
        val icon: String,
        val title: String,
        val directory: Boolean
    )


    //-----------------------------------------------------------------------------------------------------------------
    class Builder(
        private val archetypeLocations: List<ObjectLocation>
    ) {
        private var current: SidebarModel? = null


        fun update(graphStructure: GraphStructure): SidebarModel {
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


        private fun buildFrom(notation: GraphNotation): SidebarModel {
            val mainPaths = AutoConventions.mainDocuments(notation)
                .sortedBy { it.asString().lowercase() }

            val existingPaths = notation.documents.map.keys.toSet()

            val archetypes = archetypeLocations.map { location ->
                val coalesced = notation.coalesce[location]
                    ?: error("Archetype not in coalesce: $location")

                val icon = coalesced
                    .get(AutoConventions.iconAttributePath)
                    ?.asString()
                    ?: ""

                val title = coalesced
                    .get(AutoConventions.titleAttributePath)
                    ?.asString()
                    ?: location.objectPath.name.value

                val directory = (notation.firstAttribute(location, AutoConventions.directoryAttributePath)
                    as? ScalarAttributeNotation)
                    ?.asBoolean()
                    ?: false

                ArchetypeInfo(location, icon, title, directory)
            }

            val byLocation = archetypes.associateBy { it.location }

            val archetypeOfDocument = mainPaths.mapNotNull { path ->
                val archetypeLocation = DocumentArchetype.archetypeLocation(notation, path)
                    ?: return@mapNotNull null
                val info = byLocation[archetypeLocation]
                    ?: return@mapNotNull null
                path to info
            }.toMap()

            return SidebarModel(mainPaths, existingPaths, archetypes, archetypeOfDocument)
        }
    }
}
