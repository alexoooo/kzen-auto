package tech.kzen.auto.client.objects.document.custom.view

import tech.kzen.auto.client.objects.document.custom.view.obj.CustomObjectInfo
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions


// Per-object projected slice consumed by CustomView. The Builder reuses each Entry by ObjectLocation
// when its info is data-class-equal to the previous projection, so the per-object props delivered to
// CustomObject stay reference-stable across notation events that don't touch that object — RPureComponent
// shallow SCU then bails for unchanged siblings when one CustomObject is edited.
data class CustomViewModel(
    val orderedEntries: List<Entry>
) {
    //-----------------------------------------------------------------------------------------------------------------
    data class Entry(
        val objectLocation: ObjectLocation,
        val info: CustomObjectInfo
    )


    //-----------------------------------------------------------------------------------------------------------------
    class Builder {
        private var current: CustomViewModel? = null


        fun update(
            documentPath: DocumentPath,
            serverNotation: DocumentObjectNotation,
            graphStructure: GraphStructure
        ): CustomViewModel {
            val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
            val exportsState = CustomViewExports.current(serverNotation, graphStructure, mainObjectLocation)

            val prevEntriesByLocation: Map<ObjectLocation, Entry> =
                current?.orderedEntries?.associateBy { it.objectLocation } ?: emptyMap()

            val nextEntries = buildList(serverNotation.notations.map.size) {
                for ((objectPath, _) in serverNotation.notations.map) {
                    if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                        continue
                    }
                    val objectLocation = ObjectLocation(documentPath, objectPath)
                    val nextInfo = CustomObjectInfo.derive(objectLocation, graphStructure, exportsState.membership)
                    val prevEntry = prevEntriesByLocation[objectLocation]
                    val stableEntry =
                        if (prevEntry != null && prevEntry.info == nextInfo) {
                            prevEntry
                        }
                        else {
                            Entry(objectLocation, nextInfo)
                        }
                    add(stableEntry)
                }
            }

            val next = CustomViewModel(nextEntries)
            val prev = current
            return if (prev != null && prev == next) {
                prev
            }
            else {
                current = next
                next
            }
        }
    }
}
