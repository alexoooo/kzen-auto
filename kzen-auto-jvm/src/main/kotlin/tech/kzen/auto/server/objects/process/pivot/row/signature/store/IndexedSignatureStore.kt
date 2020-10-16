package tech.kzen.auto.server.objects.process.pivot.row.signature.store

import tech.kzen.auto.server.objects.process.pivot.row.signature.RowSignature


interface IndexedSignatureStore: AutoCloseable {
    fun add(signature: RowSignature)

    fun get(signatureOrdinal: Long): RowSignature
}