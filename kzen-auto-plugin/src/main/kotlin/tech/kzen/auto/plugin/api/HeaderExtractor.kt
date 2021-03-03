package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput


interface HeaderExtractor<Output> {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun <T> ofLiteral(vararg headerColumns: String): HeaderExtractor<T> {
            return object : HeaderExtractor<T> {
                override fun extract(processed: TraversableProcessorOutput<T>): List<String> {
                    return listOf(*headerColumns)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun extract(
        processed: TraversableProcessorOutput<Output>
    ): List<String>
}