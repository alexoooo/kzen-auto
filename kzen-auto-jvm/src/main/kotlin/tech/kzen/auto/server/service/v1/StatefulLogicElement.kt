package tech.kzen.auto.server.service.v1


interface StatefulLogicElement<in T> {
    fun loadState(previous: T)
}