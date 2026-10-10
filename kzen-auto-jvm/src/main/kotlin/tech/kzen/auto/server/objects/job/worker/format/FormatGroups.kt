package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.data.write.RecordOutputState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata


/**
 * The outputs one `Format` has open, by group, in the order they opened, and the groups whose outputs it has ended:
 * what a [FormatChunker] writes into, and what a live edit carries to the next one.
 */
internal class FormatGroups {
    /** One open output: its encoder [state], and the metadata of its last chunk, reused while the parent repeats. */
    class Group(
        val key: String,
        val state: RecordOutputState,
        val value: DataValue
    ) {
        var incoming: ValueMetadata? = null
        var metadata: ValueMetadata? = null
    }


    val open = LinkedHashMap<String, Group>()
    val ended = HashSet<String>()

    /** The group of the last record, still open unless an edit discarded it; its key outlives it. */
    var last: Group? = null
    var lastKey: String? = null

    var done = 0L
}
