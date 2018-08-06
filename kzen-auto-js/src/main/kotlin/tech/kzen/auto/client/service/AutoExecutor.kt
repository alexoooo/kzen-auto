//package tech.kzen.auto.client.service
//
//import tech.kzen.auto.common.api.AutoAction
//import tech.kzen.lib.common.context.ObjectGraph
//import tech.kzen.lib.common.notation.model.ProjectNotation
//
//
//class AutoExecutor(
////        private val projectNotation: ProjectNotation
//        private val objectGraph: ObjectGraph
//) {
////    companion object {
////        fun of(projectNotation: ProjectNotation): AutoExecutor {
////            val graph = AutoModelService.graph(projectNotation)
////            return AutoExecutor(graph)
////        }
////    }
//
//    fun run(actionName: String) {
////        val graph = AutoModelService.graph(projectNotation)
//
//        val actionInstance = objectGraph.get(actionName) as AutoAction
//
//        actionInstance.perform()
//    }
//}