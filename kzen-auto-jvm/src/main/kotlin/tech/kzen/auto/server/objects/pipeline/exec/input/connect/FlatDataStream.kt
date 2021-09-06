package tech.kzen.auto.server.objects.pipeline.exec.input.connect

import tech.kzen.auto.server.objects.pipeline.exec.input.model.ReadResult


interface FlatDataStream: AutoCloseable {
    fun read(byteArray: ByteArray): ReadResult
}