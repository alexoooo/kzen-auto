package tech.kzen.auto.common.paradigm.common.api

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionValue


interface ObjectState {
    fun inspect(): ExecutionValue
}