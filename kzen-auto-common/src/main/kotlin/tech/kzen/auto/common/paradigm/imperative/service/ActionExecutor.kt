package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.lib.common.model.locate.ObjectLocation


interface ActionExecutor {
    suspend fun execute(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ExecutionResult


    suspend fun control(
            actionLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ControlTransition
}