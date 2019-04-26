package tech.kzen.auto.server.service.imperative

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionInitializer
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.platform.collect.persistentListOf


object EmptyExecutionInitializer: ExecutionInitializer {
    override suspend fun initialExecutionModel(
            host: DocumentPath
    ): ImperativeModel {
        return ImperativeModel(persistentListOf())
    }
}
