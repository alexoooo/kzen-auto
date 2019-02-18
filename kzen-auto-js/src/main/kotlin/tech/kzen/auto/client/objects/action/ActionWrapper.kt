//package tech.kzen.auto.client.objects.action
//
//import react.RBuilder
//import react.ReactElement
//import tech.kzen.auto.common.exec.ExecutionState
//import tech.kzen.lib.common.api.model.ObjectLocation
//import tech.kzen.lib.common.api.model.ObjectPath
//import tech.kzen.lib.common.structure.GraphStructure
//
//
//interface ActionWrapper {
//    fun priority(): Int
//
//
//    fun isApplicableTo(
//            objectName: ObjectPath,
//            graphStructure: GraphStructure
//    ): Boolean
//
//
//    fun render(
//            rBuilder: RBuilder,
//
//            objectLocation: ObjectLocation,
//
//            graphStructure: GraphStructure,
//
//            executionState: ExecutionState?
//    ): ReactElement
//}