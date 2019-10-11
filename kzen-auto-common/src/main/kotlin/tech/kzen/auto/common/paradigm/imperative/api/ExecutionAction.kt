package tech.kzen.auto.common.paradigm.imperative.api

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel


interface ExecutionAction {
    suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult
}