package tech.kzen.auto.client.api

import react.RBuilder
import react.RHandler


interface ReactWrapper<T: react.Props> {
    fun child(input: RBuilder, handler: RHandler<T>)
}