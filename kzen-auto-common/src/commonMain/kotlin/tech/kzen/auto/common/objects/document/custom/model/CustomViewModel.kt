package tech.kzen.auto.common.objects.document.custom.model

import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.objects.document.custom.create.CustomCreation
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions


// Projected slice consumed by CustomView: the per-object entries plus the graph-wide prototype list backing the
// "+ Add" picker. The Builder reuses each Entry by ObjectLocation when its info is data-class-equal to the previous
// projection, so the per-object props delivered to CustomObject stay reference-stable across notation events that
// don't touch that object — RPureComponent shallow SCU then bails for unchanged siblings when one CustomObject is
// edited. Likewise the whole model instance is reused when nothing changed, so the store's updateIfChanged
// suppresses no-op publishes; do NOT "simplify" the Builder into always returning a fresh instance.
data class CustomViewModel(
    val orderedEntries: List<Entry>,
    val prototypes: List<CustomCreation>
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

            // Graph-wide (not document-scoped): a prototype added in another document must show up here, which is
            // why the Builder runs per notation event rather than per this document's own state change.
            val prototypes = CustomConventions.listPrototypes(graphStructure)

            val next = CustomViewModel(nextEntries, prototypes)
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
