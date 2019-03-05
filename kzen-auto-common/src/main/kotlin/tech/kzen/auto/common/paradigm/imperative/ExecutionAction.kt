package tech.kzen.auto.common.paradigm.imperative

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult


interface ExecutionAction {
    suspend fun perform(): ExecutionResult
}