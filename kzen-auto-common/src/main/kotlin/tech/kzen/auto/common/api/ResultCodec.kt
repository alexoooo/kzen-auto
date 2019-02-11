package tech.kzen.auto.common.api


interface ResultCodec {
    fun encode(instance: Any): ByteArray
    fun decode(bytes: ByteArray): Any
}