package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.lib.common.model.locate.ObjectLocation


interface ActionExecutor {
    suspend fun execute(actionLocation: ObjectLocation): ImperativeResult
}