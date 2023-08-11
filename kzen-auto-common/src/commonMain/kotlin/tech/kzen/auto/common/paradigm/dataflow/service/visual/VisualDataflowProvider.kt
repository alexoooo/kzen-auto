package tech.kzen.auto.common.paradigm.dataflow.service.visual

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


interface VisualDataflowProvider {
    suspend fun inspectDataflow(
            host: DocumentPath
    ): VisualDataflowModel


    suspend fun inspectVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexModel


//    suspend fun initialVertexState(
//            host: DocumentPath,
//            parentLocation: ObjectLocation
//    ): ExecutionValue?


    suspend fun executeVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition


    suspend fun resetDataflow(
            host: DocumentPath
    ): VisualDataflowModel
}