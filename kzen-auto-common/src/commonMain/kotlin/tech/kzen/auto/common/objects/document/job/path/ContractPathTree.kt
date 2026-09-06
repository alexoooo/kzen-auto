package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind


/**
 * What the design-time path picker offers at each position of the upstream contract (E8 item 2), one level
 * at a time so a recursive reference is expanded only on demand and nothing about the source is executed:
 * a record's fields; for a list or map field, its `[*]` element (a scalar element is itself the leaf, a map's
 * entry offers `key` and `value`); a reference stays a collapsed node until it is expanded. Derived purely
 * from the contract through [PathBinding.resolve], so what is offered is exactly what the runtime binds.
 */
object ContractPathTree {
    sealed interface Kind {
        data class Leaf(val scalar: ScalarKind): Kind
        data object Record: Kind
        data object List: Kind
        data object Map: Kind
        data class Reference(val id: String): Kind
        data class Unsupported(val description: String): Kind
    }


    /** One offered position: its [path], what is there, and whether it can be picked (a scalar leaf) or opened. */
    data class Candidate(
        val path: ProjectionPath,
        val label: String,
        val kind: Kind
    ) {
        val selectable: Boolean
            get() = kind is Kind.Leaf

        val expandable: Boolean
            get() = kind is Kind.Record || kind is Kind.List || kind is Kind.Map || kind is Kind.Reference
    }


    /** The top-level fields of [upstream] (empty when it is not a record). */
    fun roots(upstream: DataContract): List<Candidate> =
        fields(upstream, null)


    /**
     * What lies below [candidate]: a record's fields, a list's `[*]` element, a map's `key` / `value` under
     * `[*]`, a reference's definition fields. Empty for a leaf or an unsupported node, and on a path the
     * contract no longer has (the picker names the invalid path from the binding instead).
     */
    fun children(upstream: DataContract, candidate: Candidate): List<Candidate> {
        return when (candidate.kind) {
            is Kind.Leaf, is Kind.Unsupported -> emptyList()
            Kind.Record, is Kind.Reference -> fields(upstream, candidate.path)
            Kind.List, Kind.Map -> {
                val unnested = ProjectionPath(candidate.path.segments + ProjectionPathSegment.Wildcard)
                at(upstream, unnested)
            }
        }
    }


    // The candidates at [path]: a scalar element is the leaf itself; a record's fields; a map entry's key / value
    private fun at(upstream: DataContract, path: ProjectionPath): List<Candidate> {
        val resolved = PathBinding.resolve(upstream, path) as? PathBinding.Resolution.At
            ?: return emptyList()
        if (resolved.awaitingEntry) {
            val mapping = resolved.contract.expanded().structural as? DataType.Mapping
                ?: return emptyList()
            return listOf(
                Candidate(ProjectionPath(path.segments + ProjectionPathSegment.Field("key")), "key", kindOf(mapping.key)),
                Candidate(ProjectionPath(path.segments + ProjectionPathSegment.Field("value")), "value", kindOf(mapping.value)))
        }
        val structural = resolved.contract.expanded().structural
        if (structural is DataType.Record) {
            return fields(upstream, path)
        }
        return listOf(Candidate(path, ProjectionPathSegment.Wildcard.text, kindOf(structural)))
    }


    private fun fields(upstream: DataContract, path: ProjectionPath?): List<Candidate> {
        val contract = if (path == null) {
            upstream
        }
        else {
            (PathBinding.resolve(upstream, path) as? PathBinding.Resolution.At)?.contract
                ?: return emptyList()
        }
        val record = contract.expanded().structural as? DataType.Record
            ?: return emptyList()
        return record.fields
            .filter { it.id.occurrence == 0 }
            .map { field ->
                val segments = (path?.segments ?: emptyList()) + ProjectionPathSegment.Field(field.id.name)
                Candidate(ProjectionPath(segments), field.id.name, kindOf(field.type))
            }
    }


    private fun kindOf(type: DataType): Kind =
        when (type) {
            is DataType.Scalar -> Kind.Leaf(type.kind)
            is DataType.Record -> Kind.Record
            is DataType.Listing -> Kind.List
            is DataType.Mapping -> Kind.Map
            is DataType.Reference -> Kind.Reference(type.id.value)
            is DataType.Union, is DataType.Opaque, is DataType.Dynamic -> Kind.Unsupported(PathBinding.describe(type))
        }
}
