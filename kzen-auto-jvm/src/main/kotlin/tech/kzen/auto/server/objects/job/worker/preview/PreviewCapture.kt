package tech.kzen.auto.server.objects.job.worker.preview

import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.exec.data.type.*
import tech.kzen.lib.common.exec.data.value.*
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

/** Partial display capture; strict result snapshots remain governed by DataSnapshot. */
class PreviewCapture(
    private val maximumDepth: Int = 16,
    private val maximumChildren: Int = 200,
    private val maximumNodes: Int = 2_000,
    private val maximumText: Int = 4_096,
    private val maximumBytes: Int = 256 * 1_024,
    private val maximumNanos: Long = 50_000_000
) {
    fun capture(value: DataValue): PreviewNode = Writer(value).capture()

    private inner class Writer(private val value: DataValue) {
        private val started = System.nanoTime()
        private val ancestors = IdentityHashMap<Any, Boolean>()
        private var nodes = 0
        private var bytes = 0

        fun capture(): PreviewNode {
            val result = read(value.root, value.contract, 0)
            return if (result.encode().toByteArray(Charsets.UTF_8).size <= maximumBytes) result
                else marker("truncated", "Item exceeds preview size limit")
        }

        private fun exhausted(): Boolean = nodes >= maximumNodes || bytes >= maximumBytes - markerReserveBytes ||
                System.nanoTime() - started > maximumNanos

        private fun read(node: DataNode, declared: DataContract, depth: Int): PreviewNode = guarded {
            if (depth > maximumDepth || exhausted()) return@guarded marker("truncated", "Preview limit reached")
            nodes++
            bytes += nodeOverheadBytes
            when (value.access.state(node)) {
                DataState.Absent -> return@guarded PreviewNode("absent", "Absent")
                DataState.Null -> return@guarded PreviewNode("null", "null")
                DataState.Present -> {}
            }
            val contract = if (declared.structural is DataType.Dynamic) value.access.contract(node).expanded()
                else declared.expanded()
            val type = contract.structural
            if (type is DataType.Opaque) return@guarded marker("unavailable", "Opaque value")
            if (type is DataType.Dynamic || type is DataType.Reference) {
                return@guarded marker("unavailable", "Runtime structure unavailable")
            }
            if (type is DataType.Scalar) return@guarded scalar(value.access.scalar(node))
            val identity = if (contract.nativeByPath.containsKey(DataTypePath.root)) value.access.native(node) else null
            if (identity != null && ancestors.containsKey(identity)) return@guarded marker("cycle", "Circular reference")
            if (identity != null) ancestors[identity] = true
            try {
                when (type) {
                    is DataType.Record -> container("record", "${type.fields.size} ${if (type.fields.size == 1) "field" else "fields"}", type.fields.size) { index ->
                        val field = type.fields[index]
                        val child = guarded {
                            read(value.access.field(node, field.id), contract.child(DataPathSegment.Field(field.id)), depth + 1)
                        }
                        bytes += field.id.name.length * maximumEncodedBytesPerChar
                        child.copy(name = field.id.name, occurrence = field.id.occurrence)
                    }
                    is DataType.Listing -> {
                        val size = value.access.size(node)
                        container("list", "$size items", size) { index ->
                            read(value.access.element(node, index), contract.child(DataPathSegment.ListingElement), depth + 1)
                                .copy(name = "[$index]")
                        }
                    }
                    is DataType.Mapping -> {
                        val size = value.access.size(node)
                        container("map", "$size entries", size) { index ->
                            guarded {
                                nodes += 2
                                bytes += 2 * nodeOverheadBytes
                                if (exhausted()) return@guarded marker("truncated", "Preview limit reached")
                                val key = value.access.keyAt(node, index)
                                val keyCopy = scalar(key).copy(name = "key")
                                val child = read(value.access.entry(node, key), contract.child(DataPathSegment.MappingValue), depth + 1)
                                    .copy(name = "value")
                                PreviewNode("entry", keyCopy.text, listOf(keyCopy, child), "[$index]",
                                    partial = keyCopy.partial || child.partial)
                            }
                        }
                    }
                    is DataType.Union -> {
                        val active = value.access.activeVariant(node)
                        val child = read(value.access.selected(node), contract.child(DataPathSegment.Variant(active)), depth + 1)
                        PreviewNode("union", active.value, listOf(child.copy(name = active.value)), partial = child.partial)
                    }
                }
            }
            finally {
                if (identity != null) ancestors.remove(identity)
            }
        }

        private fun container(kind: String, text: String, size: Int, child: (Int) -> PreviewNode): PreviewNode {
            val children = mutableListOf<PreviewNode>()
            for (index in 0 until minOf(size, maximumChildren)) {
                if (exhausted()) break
                children += guarded { child(index) }
            }
            if (children.size < size) {
                children += marker("truncated", "${size - children.size} more omitted").copy(name = "…", occurrence = -1)
            }
            return PreviewNode(kind, text, children.toList(), partial = children.any { it.partial })
        }

        private fun scalar(scalar: ScalarExecutionValue): PreviewNode {
            val text = when (scalar) {
                is TextExecutionValue -> scalar.value
                is BooleanExecutionValue -> scalar.value.toString()
                is LongExecutionValue -> scalar.value.toString()
                is NumberExecutionValue -> scalar.value.toString()
                is BinaryExecutionValue -> {
                    val excerpt = scalar.value.take(binaryExcerptBytes).joinToString(" ") { "%02x".format(it.toInt() and 255) }
                    return PreviewNode("binary", "$excerpt (${scalar.value.size} bytes)", partial = scalar.value.size > binaryExcerptBytes)
                }
                is BinaryHandleExecutionValue -> return marker("unavailable", "Binary content is not copied")
            }
            val length = minOf(maximumText, ((maximumBytes - bytes - markerReserveBytes) / maximumEncodedBytesPerChar).coerceAtLeast(0))
            val clipped = text.take(length)
            bytes += clipped.length * maximumEncodedBytesPerChar
            return PreviewNode("scalar", clipped + if (text.length > length) "…" else "", partial = text.length > length)
        }

        private inline fun guarded(block: () -> PreviewNode): PreviewNode = try {
            block()
        }
        catch (e: CancellationException) { throw e }
        catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw e }
        catch (e: RuntimeException) {
            marker("unavailable", "Could not read: ${(e.message ?: "value unavailable").take(160)}")
        }
    }

    companion object {
        private const val markerReserveBytes = 1_024
        private const val nodeOverheadBytes = 128
        private const val maximumEncodedBytesPerChar = 6
        private const val binaryExcerptBytes = 64
    }

    private fun marker(kind: String, text: String) = PreviewNode(kind, text, partial = true)
}
