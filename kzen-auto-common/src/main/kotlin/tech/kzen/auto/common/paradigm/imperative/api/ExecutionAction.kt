package tech.kzen.auto.common.paradigm.imperative.api

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult


interface ExecutionAction {
    suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult
}