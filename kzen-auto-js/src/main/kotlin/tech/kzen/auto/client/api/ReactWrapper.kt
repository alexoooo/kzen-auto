package tech.kzen.auto.client.api

import react.RBuilder
import react.RHandler
import react.RProps
import react.ReactElement


interface ReactWrapper<T: RProps> {
    fun child(input: RBuilder, handler: RHandler<T>): ReactElement
}