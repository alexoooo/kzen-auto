package tech.kzen.auto.server.data.write


/**
 * One record's cells, read by index by a [RecordEncoder]. [text] may return a reused view, valid only until the next
 * call on this object, so a row held in a character buffer (or a number rendered into a reused builder) is read
 * without allocating. [text] is not called for a null cell.
 */
interface RecordCells {
    val size: Int

    fun isNull(index: Int): Boolean

    fun text(index: Int): CharSequence
}
