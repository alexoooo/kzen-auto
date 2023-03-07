package tech.kzen.auto.client.api

import js.core.JsoDsl
import react.ChildrenBuilder

//import react.RBuilder
//import react.RHandler


interface ReactWrapper<T: react.Props> {
//    fun child(input: RBuilder, handler: RHandler<T>)
//    fun child(builder: ChildrenBuilder, block: @JsoDsl T.() -> Unit)
    fun ChildrenBuilder.child(block: @JsoDsl T.() -> Unit)

    fun child(builder: ChildrenBuilder, block: @JsoDsl T.() -> Unit) {
        builder.apply {
            child(block)
        }
    }
}