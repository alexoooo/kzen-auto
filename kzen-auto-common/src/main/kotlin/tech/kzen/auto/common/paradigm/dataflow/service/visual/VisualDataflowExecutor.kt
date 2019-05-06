//package tech.kzen.auto.common.paradigm.dataflow.service.visual
//
//import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexTransition
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.locate.ObjectLocation
//
//
//// TODO: add support for multiple executions per host
//interface VisualDataflowExecutor {
//    suspend fun execute(
//            host: DocumentPath,
//            vertexLocation: ObjectLocation
//    ): VisualVertexTransition
//}