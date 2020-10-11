package tech.kzen.auto.server.objects.process.pivot.row.signature


interface RowSignatureIndex {
    fun size(): Long
    fun getOrAddIndex(rowValueIndexes: RowSignature): Long
    fun getCombo(comboIndex: Long): RowSignature
}