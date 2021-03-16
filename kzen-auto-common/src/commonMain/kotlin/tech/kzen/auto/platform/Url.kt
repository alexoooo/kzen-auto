package tech.kzen.auto.platform

import tech.kzen.lib.common.util.Digestible


// see: https://github.com/chRyNaN/uri
expect class Url: Digestible {
    companion object {
        fun of(url: String): Url
        fun parse(url: String): Url?
    }

    val scheme: String
    val path: String
    val query: String?
}