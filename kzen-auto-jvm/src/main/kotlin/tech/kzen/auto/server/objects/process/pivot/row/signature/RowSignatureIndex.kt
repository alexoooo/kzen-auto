package tech.kzen.auto.server.objects.process.pivot.row.signature


interface RowSignatureIndex: AutoCloseable {
    fun size(): Long
    fun getOrAddIndex(rowSignature: RowSignature): Long
    fun getSignature(signatureOrdinal: Long): RowSignature
}