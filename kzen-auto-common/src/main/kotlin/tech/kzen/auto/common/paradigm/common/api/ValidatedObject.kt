package tech.kzen.auto.common.paradigm.common.api


interface ValidatedObject {
    fun validate(): String?
}