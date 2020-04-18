//package tech.kzen.auto.common.paradigm.dataflow.service.visual
//
//import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
//import tech.kzen.lib.common.model.document.DocumentPath
//
//
//interface VisualDataflowInitializer {
//    companion object {
//        val empty = object: VisualDataflowInitializer {
//            override suspend fun initialModel(host: DocumentPath): VisualDataflowModel {
//                return VisualDataflowModel.empty
//            }
//        }
//    }
//
//
//    suspend fun initialModel(host: DocumentPath): VisualDataflowModel
//}