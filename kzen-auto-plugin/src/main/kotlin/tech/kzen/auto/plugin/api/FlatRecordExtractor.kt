package tech.kzen.auto.plugin.api

import tech.kzen.auto.plugin.model.FlatRecordBuilder


interface FlatRecordExtractor<Output> {
    fun extract(output: Output, builder: FlatRecordBuilder)
}