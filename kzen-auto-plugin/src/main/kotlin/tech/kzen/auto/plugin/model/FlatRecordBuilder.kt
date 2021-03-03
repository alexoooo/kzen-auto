package tech.kzen.auto.plugin.model


interface FlatRecordBuilder {
    fun add(value: String)
    fun addAll(values: List<String>)


    fun fieldCount(): Int
    fun fieldContentLength(): Int


    fun write(fieldContents: CharArray, fieldEnds: IntArray)
}