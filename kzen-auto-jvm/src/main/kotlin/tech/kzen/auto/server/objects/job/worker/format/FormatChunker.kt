package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.util.digest.Digest


/**
 * Format's encoding of one stream of values into chunks: each value is one chunk, a [PooledBytes] from [pool] with
 * metadata `{name, parent}`. An output is a run of consecutive values with the same name, so the chunk that starts
 * one (the first, and each whose name differs from the previous) begins with the encoder's header.
 *
 * [encoding] identifies the bytes it writes (format, columns, header): a chunker of the same encoding may continue
 * the output another one left open ([resume]), any other must start over ([restart]).
 *
 * Allocates nothing per value once the pool is warm, as long as the name and the incoming metadata object repeat;
 * a new metadata object costs the chunk's [ChunkMetadata], whose contract is reused while the parent's is equal.
 */
internal class FormatChunker(
    private val encoder: RecordEncoder,
    private val cells: FormatCells,
    private val name: FormatName,
    private val pool: RecyclablePool<PooledBytes>,
    val encoding: Digest
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /** The chunks' contract: [PooledBytes.contract] with `{name, parent}`, `parent` only when [parent] is known. */
        fun contract(parent: DataContract?): DataContract =
            PooledBytes.contract.withMetadata(MetadataContract.of(ChunkMetadata.contract(parent)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var currentName: String? = null
    private var restarting = false
    private var incoming: ValueMetadata? = null
    private var metadata: ValueMetadata? = null
    private var nameValue: DataValue? = null
    private var shape: ChunkMetadata.Shape? = null


    //-----------------------------------------------------------------------------------------------------------------
    /** The name of the output the last chunk belongs to; null before the first chunk. */
    fun openName(): String? =
        currentName


    /** Continues the output named [openName] that a chunker of the same [encoding] left open: no header for it. */
    fun resume(openName: String) {
        currentName = openName
    }


    /** Gives up the output a chunker of another encoding left open: the next chunk starts one, marked a restart. */
    fun restart() {
        restarting = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The chunk of [element], the [ordinal]-th value (from 1), which names it when encoding fails. */
    fun chunk(element: DataValue, ordinal: Long): DataValue {
        val slot = pool.acquire()
        val buffer = slot.bytes.buffer()
        buffer.bytesLength = 0

        var headerLength = 0
        try {
            cells.bind(element)
            val resolved = name.resolve(element)
            if (!resolved.contentEquals(currentName)) {
                encoder.encodeHeader(buffer)
                headerLength = buffer.bytesLength
                currentName = resolved.toString()
                nameValue = null
                metadata = null
            }
            encoder.encodeRecord(cells, buffer)
        }
        catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Record $ordinal: ${e.message}", e)
        }
        catch (e: DataAccessException) {
            throw IllegalArgumentException("Record $ordinal: ${e.message}", e)
        }
        slot.bytes.mark(headerLength, restarting)
        restarting = false

        val parent = element.metadata
        if (metadata == null || parent !== incoming) {
            metadata = chunkMetadata(parent)
            incoming = parent
        }
        return slot.value(metadata)
    }


    private fun chunkMetadata(parent: ValueMetadata?): ValueMetadata {
        val name = nameValue
            ?: ChunkMetadata.name(checkNotNull(currentName)).also { nameValue = it }

        val parentContract = parent?.value?.payloadContract
        val current = shape
        val reused =
            if (current != null && current.parent == parentContract) current
            else ChunkMetadata.Shape(parentContract).also { shape = it }

        return reused.metadata(name, parent)
    }
}
