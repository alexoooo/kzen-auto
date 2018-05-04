package tech.kzen.auto.client.objects

interface Executable<I, out T> {
    fun execute(input: I): T
}