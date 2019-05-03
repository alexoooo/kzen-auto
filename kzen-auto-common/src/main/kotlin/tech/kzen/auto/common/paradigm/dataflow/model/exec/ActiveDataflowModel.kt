package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.lib.common.model.locate.ObjectLocation


// TODO: unify with ImperativeModel
// TODO: add frames
class ActiveDataflowModel(
        val vertices: MutableMap<ObjectLocation, ActiveVertexModel>,

        // TODO: factor out
        val dataflowDag: DataflowDag
)