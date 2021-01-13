package tech.kzen.auto.server.objects.report.pipeline.input.connect

import java.io.InputStream


interface FlatData {
    fun key(): String

    fun outerExtension(): String
    fun innerExtension(): String

    fun size(): Long
    fun open(): InputStream
}