package tech.kzen.auto.client.api

import js.core.JsoDsl
import react.ChildrenBuilder


interface ReactWrapper<T: react.Props> {
    fun ChildrenBuilder.child(block: @JsoDsl T.() -> Unit)

    fun child(builder: ChildrenBuilder, block: @JsoDsl T.() -> Unit) {
        builder.apply {
            child(block)
        }
    }
}