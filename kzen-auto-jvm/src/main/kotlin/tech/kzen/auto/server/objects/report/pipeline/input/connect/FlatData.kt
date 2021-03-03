package tech.kzen.auto.server.objects.report.pipeline.input.connect

import java.io.InputStream
import java.net.URI


interface FlatData {
    fun key(): URI

    fun outerExtension(): String
    fun innerExtension(): String

    /**
     * @return estimate number of bytes remaining, or -1 if unknown
     */
    fun size(): Long

    fun open(): InputStream
}