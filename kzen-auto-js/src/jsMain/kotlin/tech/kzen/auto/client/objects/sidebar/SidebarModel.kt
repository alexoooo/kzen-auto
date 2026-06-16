package tech.kzen.auto.client.objects.sidebar

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentForm
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


// Projected slice of GraphStructure consumed by the sidebar subtree. Maintained by Builder so
// that reference identity changes only when the projected slice actually changes — attribute-only
// Notation mutations leave the model reference untouched, so RPureComponent's default shallow
// SCU bails on the sidebar without any custom override.
data class SidebarModel(
    val mainDocumentPaths: List<DocumentPath>,
    val existingDocumentPaths: Set<DocumentPath>,
    val archetypes: List<ArchetypeInfo>,
    val archetypeOfDocument: Map<DocumentPath, ArchetypeInfo>,
    val rootChildren: List<SidebarNode>
) {
    //-----------------------------------------------------------------------------------------------------------------
    data class ArchetypeInfo(
        val location: ObjectLocation,
        val icon: String,
        val title: String,
        val directory: Boolean
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Nested sidebar tree. A document (file OR directory-document like Feature) is always a LEAF — it is opened,
    // not expanded. A folder is a pure directory (DocumentForm.Folder): it has no document of its own and is
    // derived either from an explicit empty-folder entry or implied by the nesting of the documents it contains.
    sealed interface SidebarNode

    data class SidebarFileNode(
        val path: DocumentPath,
        val archetype: ArchetypeInfo
    ): SidebarNode

    data class SidebarFolderNode(
        val name: String,
        val folderPath: DocumentPath,        // the folder's own path (DocumentForm.Folder) — for delete
        val contentNesting: DocumentNesting, // where this folder's children live (= folderPath.nesting + name)
        val children: List<SidebarNode>
    ): SidebarNode


    //-----------------------------------------------------------------------------------------------------------------
    // First openable document for auto-navigation — never a folder.
    fun firstNavigableDocument(): DocumentPath? {
        return mainDocumentPaths.firstOrNull { !it.folder }
    }


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

            val archetypeOfDocument = mainPaths
                .filter { !it.folder }
                .mapNotNull { path ->
                    val archetypeLocation = DocumentArchetype.archetypeLocation(notation, path)
                        ?: return@mapNotNull null
                    val info = byLocation[archetypeLocation]
                        ?: return@mapNotNull null
                    path to info
                }.toMap()

            val rootChildren = buildLevel(
                NotationConventions.mainDocumentNesting, mainPaths, archetypeOfDocument)

            return SidebarModel(mainPaths, existingPaths, archetypes, archetypeOfDocument, rootChildren)
        }


        // Builds the tree level whose content lives at exactly `nesting`. Documents with that nesting are leaves;
        // any deeper path (document or folder), and any explicit folder entry at this level, contributes an
        // immediate child folder, recursed into at nesting + folderName.
        private fun buildLevel(
            nesting: DocumentNesting,
            allPaths: List<DocumentPath>,
            archetypeOfDocument: Map<DocumentPath, ArchetypeInfo>
        ): List<SidebarNode> {
            val files = mutableListOf<SidebarFileNode>()
            val folderNames = linkedSetOf<String>()

            val depth = nesting.segments.size

            for (path in allPaths) {
                if (path.folder) {
                    when {
                        // an explicit (empty) folder sitting directly at this level
                        path.nesting == nesting ->
                            folderNames.add(path.name.value)

                        // a folder nested deeper implies the immediate child folder on the way down
                        path.nesting.startsWith(nesting) && path.nesting.segments.size > depth ->
                            folderNames.add(path.nesting.segments[depth].value)
                    }
                }
                else {
                    when {
                        // a document at this level → leaf (file documents and directory-documents like Feature)
                        path.nesting == nesting -> {
                            val archetype = archetypeOfDocument[path]
                            if (archetype != null) {
                                files.add(SidebarFileNode(path, archetype))
                            }
                        }

                        // a document nested deeper implies the immediate child folder containing it
                        path.nesting.startsWith(nesting) && path.nesting.segments.size > depth ->
                            folderNames.add(path.nesting.segments[depth].value)
                    }
                }
            }

            val folders = folderNames.map { name ->
                val folderPath = DocumentPath(DocumentName(name), nesting, DocumentForm.Folder)
                val contentNesting = nesting.plus(DocumentSegment(name))
                SidebarFolderNode(
                    name,
                    folderPath,
                    contentNesting,
                    buildLevel(contentNesting, allPaths, archetypeOfDocument))
            }

            // folders first (alpha), then files (alpha)
            val sortedFolders = folders.sortedBy { it.name.lowercase() }
            val sortedFiles = files.sortedBy { it.path.name.value.lowercase() }

            return sortedFolders + sortedFiles
        }
    }
}
