package tech.kzen.auto.common.service

import tech.kzen.lib.common.api.model.ObjectLocation


interface ActionExecutor {
    suspend fun execute(actionLocation: ObjectLocation)
}