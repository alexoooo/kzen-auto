package tech.kzen.auto.common.paradigm.imperative.service
//
//import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.location.ObjectLocation
//
//
//interface ActionExecutor {
//    suspend fun execute(
//            host: DocumentPath,
//            actionLocation: ObjectLocation,
//            imperativeModel: ImperativeModel
//    ): ExecutionResult
//
//
//    // TODO: use ControlResult?
//    suspend fun control(
//            host: DocumentPath,
//            actionLocation: ObjectLocation,
//            imperativeModel: ImperativeModel
//    ): ControlTransition
//
//
//    suspend fun returnFrame(
//            host: DocumentPath
//    )
//}