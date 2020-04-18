package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.document.DocumentPath


interface ExecutionInitializer {
    // TODO
    suspend fun runningHosts(): List<DocumentPath>

    suspend fun initialExecutionModel(host: DocumentPath): ImperativeModel
}