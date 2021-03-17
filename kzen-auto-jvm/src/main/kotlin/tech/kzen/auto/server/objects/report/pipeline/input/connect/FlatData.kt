package tech.kzen.auto.server.objects.report.pipeline.input.connect

import tech.kzen.auto.common.util.data.DataLocation
import java.io.InputStream


interface FlatData {
    fun key(): DataLocation

    fun outerExtension(): String
    fun innerExtension(): String

    /**
     * @return estimate number of bytes remaining, or -1 if unknown
     */
    fun size(): Long

    fun open(): InputStream
}