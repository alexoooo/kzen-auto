package tech.kzen.auto.client.objects.sidebar

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.*
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
        val directory: Boolean,
        // optional declarative flyout group (the `group` notation attribute); null = top-level create item
        val group: String?
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Nested sidebar tree. A document (file OR directory-document like Feature) is always a LEAF — it is opened,
    // not expanded. A folder is a pure directory (DocumentForm.Folder) with its own explicit notation entry (one
    // entry per directory) and no document of its own; its children are the paths nested directly under it.
    sealed interface SidebarNode

    data class SidebarDocumentNode(
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

                // the base Document archetype defaults group to "" (a meta-declared scalar must have a value, or the
                // object fails to define) — treat that empty default as "ungrouped" so untagged archetypes stay top-level
                val group = coalesced
                    .get(AutoConventions.groupAttributePath)
                    ?.asString()
                    ?.takeIf { it.isNotEmpty() }

                ArchetypeInfo(location, icon, title, directory, group)
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


        // Builds the tree level whose content lives at exactly `nesting`. Only paths AT this level matter: a
        // document is a leaf, an explicit folder entry is a child folder recursed into at nesting + folderName.
        // Every folder has its own entry (see the folder-notation unification), so no deeper-path inference is
        // needed — a folder's children surface when the recursion descends into its own content nesting.
        private fun buildLevel(
            nesting: DocumentNesting,
            allPaths: List<DocumentPath>,
            archetypeOfDocument: Map<DocumentPath, ArchetypeInfo>
        ): List<SidebarNode> {
            val files = mutableListOf<SidebarDocumentNode>()
            val folderNames = linkedSetOf<String>()

            for (path in allPaths) {
                if (path.nesting != nesting) {
                    continue
                }

                if (path.folder) {
                    // an explicit folder entry directly at this level
                    folderNames.add(path.name.value)
                }
                else {
                    // a document at this level → leaf (file documents and directory-documents like Feature)
                    val archetype = archetypeOfDocument[path]
                    if (archetype != null) {
                        files.add(SidebarDocumentNode(path, archetype))
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
