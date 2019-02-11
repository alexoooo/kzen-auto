package tech.kzen.auto.common.api

import tech.kzen.auto.common.exec.ExecutionResult


interface AutoAction {
    suspend fun perform(): ExecutionResult
}