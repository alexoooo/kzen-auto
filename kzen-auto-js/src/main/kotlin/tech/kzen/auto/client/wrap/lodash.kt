//@file:JsModule("lodash")
package tech.kzen.auto.client.wrap


@JsModule("lodash")
@JsNonModule
external val lodash: Lodash

//
//
////@JsModule("lodash")
////external val lodash: Lodash
////
////

external interface Lodash {
//    fun <K,V> debounce(functionToDebounce: (K) -> V, debounceMillis: Int): (K) -> V

    fun debounce(
            functionToDebounce: () -> Unit,
            debounceMillis: Int
    ): FunctionWithDebounce
}


// https://stackoverflow.com/questions/50557507/debounce-check-if-the-debounce-is-pending
external interface FunctionWithDebounce {
//    fun invoke()
    fun apply()

    fun cancel()
    fun flush()
}


//val lodash = window.get("_")

// https://medium.com/@ralf.stuckert/getting-started-with-kotlin-react-part-ii-9dda64c9b0c8
//val lodash: dynamic = window.get("_")
//val lodash: Lodash = window.get("_")

