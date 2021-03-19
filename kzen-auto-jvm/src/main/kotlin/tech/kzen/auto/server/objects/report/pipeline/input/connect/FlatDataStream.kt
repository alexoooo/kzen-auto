package tech.kzen.auto.server.objects.report.pipeline.input.connect

import tech.kzen.auto.server.objects.report.pipeline.input.model.ReadResult


interface FlatDataStream: AutoCloseable {
    fun read(byteArray: ByteArray): ReadResult
}