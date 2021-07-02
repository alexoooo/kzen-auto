package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.model.data.DataBlockBuffer


interface DataFramer {
    fun frame(dataBlockBuffer: DataBlockBuffer)
}