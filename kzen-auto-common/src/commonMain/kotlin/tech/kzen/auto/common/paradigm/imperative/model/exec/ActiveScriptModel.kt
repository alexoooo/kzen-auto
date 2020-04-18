package tech.kzen.auto.common.paradigm.imperative.model.exec

import tech.kzen.lib.common.model.locate.ObjectLocation


data class ActiveScriptModel(
        val steps: MutableMap<ObjectLocation, ActiveScriptModel>
)