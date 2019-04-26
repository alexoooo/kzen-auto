package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.lib.common.model.locate.ObjectLocation


// TODO: unify with ImperativeModel
// TODO: add frames
class ActiveDataflowModel(
        val vertices: MutableMap<ObjectLocation, ActiveVertexModel>
)