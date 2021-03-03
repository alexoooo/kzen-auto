package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.model.DataBlockBuffer


interface DataFramer {
    fun frame(dataBlockBuffer: DataBlockBuffer)
    fun endOfStream(dataBlockBuffer: DataBlockBuffer)
}