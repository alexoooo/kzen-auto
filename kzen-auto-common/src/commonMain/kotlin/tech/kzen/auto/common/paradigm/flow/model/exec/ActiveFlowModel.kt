package tech.kzen.auto.common.paradigm.flow.model.exec

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


// TODO: unify with ImperativeModel
// TODO: add frames
class ActiveFlowModel(
        val vertices: MutableMap<ObjectLocation, ActiveVertexModel>
) {
    @Suppress("UNUSED_PARAMETER")
    fun move(from: DocumentPath, newPath: DocumentPath) {
        val oldLocations = vertices.keys.toList()

        for (oldLocation in oldLocations) {
            val newLocation = oldLocation.copy(documentPath = newPath)

            val activeVertexModel = vertices.remove(oldLocation)!!
            vertices[newLocation] = activeVertexModel
        }
    }
}