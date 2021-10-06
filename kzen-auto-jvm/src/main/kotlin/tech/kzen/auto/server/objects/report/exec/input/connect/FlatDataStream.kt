package tech.kzen.auto.server.objects.report.exec.input.connect

import tech.kzen.auto.server.objects.report.exec.input.model.ReadResult


interface FlatDataStream: AutoCloseable {
    fun read(byteArray: ByteArray): ReadResult
}