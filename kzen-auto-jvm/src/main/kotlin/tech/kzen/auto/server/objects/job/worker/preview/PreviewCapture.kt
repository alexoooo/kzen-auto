package tech.kzen.auto.server.objects.job.worker.preview

import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.exec.data.type.*
import tech.kzen.lib.common.exec.data.value.*
import tech.kzen.lib.platform.ClassNames.simple
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

/**
 * Detached display copy of a value, read in full; strict result snapshots remain governed by DataSnapshot. A branch
 * that cannot be shown (an opaque native, a cycle, a read that fails) becomes a marker beside its readable siblings.
 */
object PreviewCapture {
    fun capture(value: DataValue): PreviewNode {
        val payload = Writer(value).capture()
        val metadata = value.metadata ?: return payload
        return payload.copy(metadata = Writer(metadata.value).capture())
    }

    private class Writer(private val value: DataValue) {
        private val ancestors = IdentityHashMap<Any, Boolean>()

        fun capture(): PreviewNode =
            read(value.root, value.contract)

        private fun read(node: DataNode, declared: DataContract): PreviewNode = guarded {
            when (value.access.state(node)) {
                DataState.Absent -> return@guarded PreviewNode("absent", "Absent")
                DataState.Null -> return@guarded PreviewNode("null", "null")
                DataState.Present -> {}
            }
            val contract = if (declared.structural is DataType.Dynamic) value.access.contract(node).expanded()
                else declared.expanded()
            val type = contract.structural
            if (type is DataType.Opaque) {
                // Never copied by design, so not partial: a larger sample or expanding would show no more
                val name = contract.nativeByPath[DataTypePath.root]?.className?.simple()
                return@guarded PreviewNode("unavailable", name ?: "Opaque value")
            }
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
                            read(value.access.field(node, field.id), contract.child(DataPathSegment.Field(field.id)))
                        }
                        child.copy(name = field.id.name, occurrence = field.id.occurrence)
                    }
                    is DataType.Listing -> {
                        val size = value.access.size(node)
                        container("list", "$size items", size) { index ->
                            read(value.access.element(node, index), contract.child(DataPathSegment.ListingElement))
                                .copy(name = "[$index]")
                        }
                    }
                    is DataType.Mapping -> {
                        val size = value.access.size(node)
                        container("map", "$size entries", size) { index ->
                            guarded {
                                val key = value.access.keyAt(node, index)
                                val keyCopy = scalar(key).copy(name = "key")
                                val child = read(
                                    value.access.entry(node, key), contract.child(DataPathSegment.MappingValue))
                                    .copy(name = "value")
                                PreviewNode("entry", keyCopy.text, listOf(keyCopy, child), "[$index]",
                                    partial = keyCopy.partial || child.partial)
                            }
                        }
                    }
                    is DataType.Union -> {
                        val active = value.access.activeVariant(node)
                        val child = read(value.access.selected(node), contract.child(DataPathSegment.Variant(active)))
                        PreviewNode("union", active.value, listOf(child.copy(name = active.value)), partial = child.partial)
                    }
                }
            }
            finally {
                if (identity != null) ancestors.remove(identity)
            }
        }

        private fun container(kind: String, text: String, size: Int, child: (Int) -> PreviewNode): PreviewNode {
            val children = List(size) { index -> guarded { child(index) } }
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
            return PreviewNode("scalar", text)
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

    // A binary scalar shows as a hex excerpt with its size
    private const val binaryExcerptBytes = 64

    private fun marker(kind: String, text: String) = PreviewNode(kind, text, partial = true)
}
