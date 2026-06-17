package tech.kzen.auto.common.objects.document.folder

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect


// Generic organizational folder: a directory document with no payload and no stage view. Exists purely so the
// sidebar can persist an (initially empty) tree container; clicking a folder row toggles expand/collapse rather
// than navigating (see SidebarFolder / SidebarModel.ArchetypeInfo.navigable). Contrast FeatureDocument, which is
// also a directory document but IS navigable and carries its own payload.
@Reflect
class FolderDocument(
    val objectLocation: ObjectLocation
):
    DocumentArchetype()
{
    companion object {
        val archetypeObjectName = ObjectName("Folder")
    }
}
