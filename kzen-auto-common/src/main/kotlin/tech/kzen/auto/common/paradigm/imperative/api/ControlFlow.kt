package tech.kzen.auto.common.paradigm.imperative.api

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlState
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition


interface ControlFlow: ExecutionAction {
    fun control(
            imperativeModel: ImperativeModel,
            controlState: ControlState
    ): ControlTransition
}