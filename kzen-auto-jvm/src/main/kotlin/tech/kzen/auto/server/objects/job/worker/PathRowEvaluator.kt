package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.path.BoundPath
import tech.kzen.auto.common.objects.document.job.path.BoundStep
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * Evaluates bound projection paths over one element into flat rows (E8 runtime rules), navigating lazily
 * through [ValueAccess] and copying every leaf as text so no row aliases the element's native storage:
 * - paths sharing an iteration key iterate the same list / map together; distinct keys form a cross product,
 *   a nested key iterating within its parent's element;
 * - a null (or absent) intermediate yields null for every leaf through it and keeps the row;
 * - an empty (or null) unnested list yields zero rows for the element it belongs to — and so for the whole
 *   input element when the list is at the top, since every row carries that path's column.
 * Rows are column-aligned with the bound paths' order. One instance serves one Worker (single-threaded).
 */
internal class PathRowEvaluator(
    private val paths: List<BoundPath>
) {
    /** One output row: a text per column (null when the leaf is null or unreachable). */
    class Row(val values: Array<String?>) {
        fun states(): List<DataState> = values.map { if (it == null) DataState.Null else DataState.Present }
        fun texts(): List<String> = values.map { it ?: "" }
    }


    // The iteration tree: a group per wildcard prefix (the root for none); its leaves are the columns whose
    // last wildcard it is, its children the next wildcards below it.
    private class Group(
        val key: List<BoundStep>,
        val leaves: MutableList<Int> = ArrayList(),
        val children: MutableList<Group> = ArrayList()
    )


    private val root = Group(emptyList())

    // The key text of the map entry being iterated, for a `key` leaf under an Entries group
    private var entryKeyText: String? = null


    init {
        val groups = LinkedHashMap<List<BoundStep>, Group>()
        groups[emptyList()] = root
        for ((column, path) in paths.withIndex()) {
            var parent = root
            for ((index, step) in path.steps.withIndex()) {
                if (!step.unnests) continue
                val key = path.steps.subList(0, index + 1)
                parent = groups.getOrPut(key) { Group(key).also { child -> parent.children.add(child) } }
            }
            parent.leaves.add(column)
        }
    }


    fun rows(element: DataValue): List<Row> =
        evaluate(root, element.access, element.root).map { Row(it) }


    // Rows of [group] positioned at [node] (the input element, or the list / map element its key unnests):
    // the group's own leaves, then the cross product of its children's rows — none when a child has none.
    private fun evaluate(group: Group, access: ValueAccess, node: DataNode): List<Array<String?>> {
        val base = arrayOfNulls<String>(paths.size)
        for (column in group.leaves) {
            base[column] = leafText(access, node, paths[column].steps.subList(group.key.size, paths[column].steps.size))
        }
        var rows: List<Array<String?>> = listOf(base)
        for (child in group.children) {
            val childRows = iterate(child, access, node, group.key.size)
            if (childRows.isEmpty()) {
                return emptyList()
            }
            rows = rows.flatMap { row -> childRows.map { childRow -> merge(row, childRow) } }
        }
        return rows
    }


    // [child]'s rows: one per element of the list / map its key unnests below [node], reached through the steps
    // after the parent's key; none when the container, or an intermediate on the way to it, is null / absent.
    private fun iterate(child: Group, access: ValueAccess, node: DataNode, parentKeySize: Int): List<Array<String?>> {
        val unnest = child.key.last()
        var container = node
        for (step in child.key.subList(parentKeySize, child.key.size - 1)) {
            container = navigate(access, container, step) ?: return emptyList()
        }
        if (access.state(container) != DataState.Present) {
            return emptyList()
        }
        val rows = ArrayList<Array<String?>>()
        for (index in 0 until access.size(container)) {
            val element = when (unnest) {
                BoundStep.Elements -> access.element(container, index)
                BoundStep.Entries -> {
                    val key = access.keyAt(container, index)
                    entryKeyText = DataReadCore.scalarText(key)
                    access.entry(container, key)
                }
                else -> throw IllegalStateException("Not an unnesting step: $unnest")
            }
            rows.addAll(evaluate(child, access, element))
        }
        return rows
    }


    // A leaf's text from [node] through its remaining [steps]; null for a null / absent value on the way
    private fun leafText(access: ValueAccess, node: DataNode, steps: List<BoundStep>): String? {
        if (steps.firstOrNull() == BoundStep.Key) {
            return entryKeyText
        }
        var current = node
        for (step in steps) {
            current = navigate(access, current, step) ?: return null
        }
        if (access.state(current) != DataState.Present) {
            return null
        }
        return DataReadCore.scalarText(access.scalar(current))
    }


    private fun navigate(access: ValueAccess, node: DataNode, step: BoundStep): DataNode? {
        if (access.state(node) != DataState.Present) {
            return null
        }
        return when (step) {
            is BoundStep.Field -> access.field(node, step.id)
            BoundStep.Value -> node
            BoundStep.Key, BoundStep.Elements, BoundStep.Entries ->
                throw IllegalStateException("Step $step is not navigated singly")
        }
    }


    private fun merge(row: Array<String?>, other: Array<String?>): Array<String?> {
        val merged = row.copyOf()
        for (index in other.indices) {
            if (other[index] != null) {
                merged[index] = other[index]
            }
        }
        return merged
    }
}
