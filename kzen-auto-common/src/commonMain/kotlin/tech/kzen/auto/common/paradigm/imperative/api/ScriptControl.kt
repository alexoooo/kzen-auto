package tech.kzen.auto.common.paradigm.imperative.api
//
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.auto.common.paradigm.imperative.model.control.ControlState
//import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
//import tech.kzen.lib.common.model.obj.ObjectName
//
//
//interface ScriptControl: ScriptStep {
//    companion object {
//        val objectName = ObjectName("ControlFlow")
//    }
//
//
//    fun control(
//            imperativeModel: ImperativeModel,
//            controlState: ControlState
//    ): ControlTransition
//}