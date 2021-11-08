package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.api.managed.TraversableReportOutput


interface HeaderExtractor<Output> {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun <T> ofLiteral(vararg headerColumns: String): HeaderExtractor<T> {
            return object : HeaderExtractor<T> {
                override fun extract(processed: TraversableReportOutput<T>): List<String> {
                    return listOf(*headerColumns)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun extract(
        processed: TraversableReportOutput<Output>
    ): List<String>
}