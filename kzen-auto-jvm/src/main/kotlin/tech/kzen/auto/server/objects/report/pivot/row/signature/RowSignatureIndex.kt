package tech.kzen.auto.server.objects.report.pivot.row.signature


interface RowSignatureIndex: AutoCloseable {
    fun size(): Long

    fun getOrAddIndex(rowSignature: RowSignature): Long
    fun getOrAddIndex(valueIndexes: LongArray): Long

    fun getSignature(signatureOrdinal: Long): RowSignature
}