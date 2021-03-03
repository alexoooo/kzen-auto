package tech.kzen.auto.server.objects.report.pipeline.input.v2.read

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.ReadResult


interface FlatDataReader: AutoCloseable {
    fun read(byteArray: ByteArray): ReadResult
}