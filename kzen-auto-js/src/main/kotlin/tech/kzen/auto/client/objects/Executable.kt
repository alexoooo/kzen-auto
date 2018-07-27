package tech.kzen.auto.client.objects

interface Executable<in I, out T> {
    fun execute(input: I): T
}