package tech.kzen.auto.platform

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


// see: https://github.com/chRyNaN/uri
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class Url: Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(url: String): Url
        fun parse(url: String): Url?
    }

    
    //-----------------------------------------------------------------------------------------------------------------
    val scheme: String
    val path: String
    val query: String?


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink)
}