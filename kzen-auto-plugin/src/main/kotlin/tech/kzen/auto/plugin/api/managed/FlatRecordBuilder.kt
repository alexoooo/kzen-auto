package tech.kzen.auto.plugin.api.managed


interface FlatRecordBuilder {
    fun add(value: String)
    fun addAll(values: List<String>)
    
    fun add(value: Long)
}