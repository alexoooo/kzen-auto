package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.overlay.MetadataShape
import tech.kzen.lib.common.util.digest.Digest


/**
 * Format's encoding of one stream of values into chunks, a [PooledBytes] from [pool] each, with metadata
 * `{group, format, parent}` ([ChunkMetadata]). Each value is one chunk of the output of its group ([groupOf], or
 * `""` for every value when null); the chunk that opens an output begins with the encoder's header. An output ends
 * with a chunk holding its footer, marked as its end: at [complete], in the order the outputs opened, or, when
 * [endsOnChange], as soon as a value of another group arrives, so a group that comes back fails.
 *
 * [encoding] identifies how it writes (format, columns, header, grouping): a chunker of the same encoding continues
 * the outputs another one left open ([resume]), any other discards them ([restart]).
 *
 * Allocates nothing per value once the pool is warm, as long as the group and the incoming metadata object repeat;
 * a new metadata object costs the chunk's metadata record, its [ChunkMetadata.shape] reused while the parent's
 * contract is the same.
 */
internal class FormatChunker(
    private val encoder: RecordEncoder,
    private val cells: FormatCells,
    private val groupOf: ((DataValue) -> String)?,
    private val endsOnChange: Boolean,
    extension: String,
    private val pool: RecyclablePool<PooledBytes>,
    val encoding: Digest
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /** The chunks' contract: [PooledBytes.contract] with [ChunkMetadata], `parent` only when [parent] is known. */
        fun contract(parent: DataContract?): DataContract =
            PooledBytes.contract.withMetadata(ChunkMetadata.shape(parent).contract)

        private const val ungrouped = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    var groups = FormatGroups()
        private set

    private val format = ChunkMetadata.format(extension)
    private var shape: MetadataShape? = null

    // Outputs a chunker of another encoding left open, discarded by the next call
    private var discarding: Collection<FormatGroups.Group> = emptyList()


    //-----------------------------------------------------------------------------------------------------------------
    /** Continues the outputs [carried] has open, which a chunker of the same [encoding] wrote. */
    fun resume(carried: FormatGroups) {
        groups = carried
    }


    /**
     * Gives up the outputs [carried] has open, which a chunker of another [encoding] wrote: the next call discards
     * each, and a group that comes back opens its output again. The groups it ended still count as ended when
     * [sameGroups] (the groups still mean what they meant).
     */
    fun restart(carried: FormatGroups, sameGroups: Boolean) {
        discarding = carried.open.values
        groups.lastKey = carried.lastKey
        groups.done = carried.done
        if (sameGroups) {
            groups.ended.addAll(carried.ended)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Adds to [out] the chunks of [element], the [ordinal]-th value (from 1), which names it when encoding fails:
     * any discards, the footer of the output it ends, then its own.
     */
    fun chunk(element: DataValue, ordinal: Long, out: MutableList<DataValue>) {
        discardCarried(out)
        try {
            cells.bind(element)
            val group = groupFor(groupOf?.invoke(element) ?: ungrouped, out)

            val slot = pool.acquire()
            val buffer = slot.bytes.buffer()
            buffer.bytesLength = 0
            var headerLength = 0
            if (group.metadata == null) {
                encoder.encodeHeader(group.state, buffer)
                headerLength = buffer.bytesLength
            }
            encoder.encodeRecord(group.state, cells, buffer)
            slot.bytes.mark(headerLength, discardsOutput = false, endsOutput = false)

            val parent = element.metadata
            if (group.metadata == null || parent !== group.incoming) {
                group.metadata = chunkMetadata(group.value, parent)
                group.incoming = parent
            }
            out += slot.value(group.metadata)
        }
        catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Record $ordinal: ${e.message}", e)
        }
        catch (e: DataAccessException) {
            throw IllegalArgumentException("Record $ordinal: ${e.message}", e)
        }
    }


    /** Adds to [out] any discards, then the footer of every open output, in the order they opened. */
    fun complete(out: MutableList<DataValue>) {
        discardCarried(out)
        for (group in groups.open.values) {
            footer(group, out)
        }
        groups.open.clear()
        groups.last = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun groupFor(key: String, out: MutableList<DataValue>): FormatGroups.Group {
        val last = groups.last
        if (last != null && last.key == key) {
            return last
        }
        val group = groups.open[key]
            ?: open(key, last, out)
        groups.last = group
        groups.lastKey = key
        return group
    }


    private fun open(key: String, last: FormatGroups.Group?, out: MutableList<DataValue>): FormatGroups.Group {
        if (endsOnChange) {
            require(key !in groups.ended) {
                "Group '$key' came back after '${groups.lastKey}'; " +
                    "set \"Write each file\" to \"At the end\", or sort by the group first"
            }
            if (last != null) {
                footer(last, out)
                groups.open.remove(last.key)
                groups.ended += last.key
            }
        }
        val opened = FormatGroups.Group(key, encoder.openOutput(), ChunkMetadata.group(key))
        groups.open[key] = opened
        return opened
    }


    private fun footer(group: FormatGroups.Group, out: MutableList<DataValue>) {
        val slot = pool.acquire()
        val buffer = slot.bytes.buffer()
        buffer.bytesLength = 0
        encoder.encodeFooter(group.state, buffer)
        slot.bytes.mark(0, discardsOutput = false, endsOutput = true)
        out += slot.value(group.metadata)
        groups.done += 1
    }


    private fun discardCarried(out: MutableList<DataValue>) {
        if (discarding.isEmpty()) {
            return
        }
        for (group in discarding) {
            val slot = pool.acquire()
            slot.bytes.buffer().bytesLength = 0
            slot.bytes.mark(0, discardsOutput = true, endsOutput = false)
            out += slot.value(group.metadata)
        }
        discarding = emptyList()
    }


    private fun chunkMetadata(group: DataValue, parent: ValueMetadata?): ValueMetadata {
        val values = ChunkMetadata.values(group, format, parent)
        val current = shape
        val reused =
            if (current != null && current.fits(null, values)) current
            else ChunkMetadata.shape(parent?.value?.payloadContract).also { shape = it }
        return reused.metadata(null, values)
    }
}
