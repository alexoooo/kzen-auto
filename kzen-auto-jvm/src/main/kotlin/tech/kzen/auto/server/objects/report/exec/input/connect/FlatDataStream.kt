package tech.kzen.auto.server.objects.report.exec.input.connect

import tech.kzen.auto.server.objects.report.exec.input.model.ReadResult
import java.io.ByteArrayOutputStream


interface FlatDataStream: AutoCloseable {
    fun read(byteArray: ByteArray): ReadResult


    fun readAll(): ByteArray {
        val all = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val result = read(buffer)
            all.write(buffer, 0, result.byteCount())
            if (result.isEndOfData()) {
                break
            }
        }
        return all.toByteArray()
    }
}