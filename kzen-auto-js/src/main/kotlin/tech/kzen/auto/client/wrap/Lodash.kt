//@file:JsModule("lodash")
package tech.kzen.auto.client.wrap

import org.w3c.dom.get
import kotlin.browser.window

//
//
////@JsModule("lodash")
////external val lodash: Lodash
////
////
external interface Lodash {
    fun <K,V> debounce(functionToDebounce: (K) -> V, debounceMillis: Int): (K) -> V
}

//val lodash = window.get("_")

// https://medium.com/@ralf.stuckert/getting-started-with-kotlin-react-part-ii-9dda64c9b0c8
//val lodash: dynamic = window.get("_")
val lodash: Lodash = window.get("_")

