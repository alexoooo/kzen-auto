package tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature.store

import tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature.RowSignature


interface IndexedSignatureStore: AutoCloseable {
    fun signatureSize(): Int

    fun add(signature: RowSignature)
    fun add(valueIndexes: LongArray)

    fun get(signatureOrdinal: Long): RowSignature
}