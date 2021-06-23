package tech.kzen.auto.plugin.api.managed


interface FlatRecordBuilder {
    fun add(value: CharSequence)
    fun addAll(values: List<String>)
    
    fun add(value: Long)
    fun add(value: Double, decimalPlaces: Int)
    fun add(value: CharArray, offset: Int, length: Int)
}