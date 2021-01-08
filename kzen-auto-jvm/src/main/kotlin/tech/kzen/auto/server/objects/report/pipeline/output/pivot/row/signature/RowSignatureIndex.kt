package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature


interface RowSignatureIndex: AutoCloseable {
    fun size(): Long
    fun signatureSize(): Int

    fun getOrAddIndex(rowSignature: RowSignature): Long
    fun getOrAddIndex(valueIndexes: LongArray): Long
    fun addIndex(valueIndexes: LongArray): Long

    fun getSignature(signatureOrdinal: Long): RowSignature
}