package tech.kzen.auto.common.paradigm.common.api

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionValue


interface StatefulObject {
//    fun state(): ObjectState
    fun inspect(): ExecutionValue
}