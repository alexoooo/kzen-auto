package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.platform.collect.persistentListOf


object EmptyExecutionInitializer: ExecutionInitializer {
    override suspend fun runningHosts(): List<DocumentPath> {
        return listOf()
    }

    override suspend fun initialExecutionModel(
            host: DocumentPath
    ): ImperativeModel {
        return ImperativeModel(null, persistentListOf())
    }
}
