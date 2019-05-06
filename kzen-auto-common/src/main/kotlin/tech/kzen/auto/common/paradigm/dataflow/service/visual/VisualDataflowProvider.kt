package tech.kzen.auto.common.paradigm.dataflow.service.visual

import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


interface VisualDataflowProvider {
    suspend fun inspect(
            host: DocumentPath
    ): VisualDataflowModel


    suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition


    suspend fun reset(
            host: DocumentPath
    ): VisualDataflowModel
}