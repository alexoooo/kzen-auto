package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.renderName


/**
 * Binds a [PathProjectionSpec] against the upstream record contract (E8 runtime rules, shared with the
 * design-time picker so both report the same errors):
 * - a field must exist on the record at that point (a recursive reference is expanded on demand, one level per
 *   step, so a recursive contract binds finitely);
 * - `[*]` unnests a list or a map — after a map's `[*]` only `key` or `value` may follow;
 * - the leaf must be scalar (a record, list or map leaf is an error pointing at Formula / Filter);
 * - output names — the path's default or its alias — must be unique; a collision names both paths.
 * The output contract is a flat record of nullable scalars in entry order (a null intermediate yields null).
 */
object PathBinding {
    private const val keyName = "key"
    private const val valueName = "value"


    fun bind(spec: PathProjectionSpec, upstream: DataContract): PathBindingResult {
        val bound = ArrayList<BoundPath>()
        val errors = ArrayList<PathBindingError>()
        for (entry in spec.entries) {
            when (val outcome = bindEntry(entry, upstream)) {
                is Outcome.Bound -> bound.add(outcome.path)
                is Outcome.Failed -> errors.add(PathBindingError(entry.path, outcome.message))
            }
        }

        val byName = LinkedHashMap<String, PathProjectionEntry>()
        for (entry in spec.entries) {
            val earlier = byName[entry.outputName]
            if (earlier == null) {
                byName[entry.outputName] = entry
            }
            else {
                errors.add(PathBindingError(
                    entry.path,
                    "output name '${entry.outputName}' collides with ${earlier.path.asString()}" +
                        "; give one of them an alias"))
            }
        }

        if (errors.isNotEmpty()) {
            return PathBindingResult(bound, null, errors)
        }
        val fields = bound.map { path ->
            DataField(FieldId(path.outputName), DataType.Scalar(path.leaf.kind, nullable = true))
        }
        return PathBindingResult(bound, DataContract(DataType.Record(fields)), errors)
    }


    private sealed interface Outcome {
        class Bound(val path: BoundPath): Outcome
        class Failed(val message: String): Outcome
    }


    /**
     * Where [path] lands in [upstream]: the contract there and the steps taken, or the error. A map's `[*]`
     * with nothing after it resolves to the map itself with [Resolution.At.awaitingEntry] set — the entry's
     * `key` / `value` are what may follow (the design-time tree offers them).
     */
    sealed interface Resolution {
        class At(val contract: DataContract, val steps: List<BoundStep>, val awaitingEntry: Boolean): Resolution
        class Failed(val message: String): Resolution
    }


    fun resolve(upstream: DataContract, path: ProjectionPath): Resolution {
        val steps = ArrayList<BoundStep>()
        var current = upstream
        var afterEntries = false
        for (segment in path.segments) {
            val structural = current.expanded().structural
            when (segment) {
                is ProjectionPathSegment.Field -> {
                    if (afterEntries) {
                        afterEntries = false
                        when (segment.name) {
                            keyName -> {
                                steps.add(BoundStep.Key)
                                current = current.child(DataPathSegment.MappingKey)
                            }
                            valueName -> {
                                steps.add(BoundStep.Value)
                                current = current.child(DataPathSegment.MappingValue)
                            }
                            else -> return Resolution.Failed(
                                "after a map's [*] only '$keyName' or '$valueName' may follow, not '${segment.name}'")
                        }
                        continue
                    }
                    val record = structural as? DataType.Record
                        ?: return Resolution.Failed(
                            "'${segment.name}' is not a field: the value here is ${describe(structural)}")
                    val field = record.fields.firstOrNull { it.id.name == segment.name && it.id.occurrence == 0 }
                        ?: return Resolution.Failed(
                            "no field '${segment.name}'; available: ${record.fields.joinToString { it.id.name }}")
                    steps.add(BoundStep.Field(field.id))
                    current = current.child(DataPathSegment.Field(field.id))
                }

                ProjectionPathSegment.Wildcard -> when (structural) {
                    is DataType.Listing -> {
                        steps.add(BoundStep.Elements)
                        current = current.child(DataPathSegment.ListingElement)
                    }
                    is DataType.Mapping -> {
                        steps.add(BoundStep.Entries)
                        afterEntries = true
                    }
                    else -> return Resolution.Failed("[*] needs a list or map, found ${describe(structural)}")
                }
            }
        }
        return Resolution.At(current, steps, afterEntries)
    }


    private fun bindEntry(entry: PathProjectionEntry, upstream: DataContract): Outcome {
        val resolution = when (val resolved = resolve(upstream, entry.path)) {
            is Resolution.Failed -> return Outcome.Failed(resolved.message)
            is Resolution.At -> resolved
        }
        if (resolution.awaitingEntry) {
            return Outcome.Failed("a map's [*] must continue into '$keyName' or '$valueName'")
        }
        val leaf = resolution.contract.expanded().structural as? DataType.Scalar
            ?: return Outcome.Failed(
                "the leaf is ${describe(resolution.contract.expanded().structural)}, not a scalar; " +
                    "use a Formula or Filter to work with it whole")
        return Outcome.Bound(BoundPath(entry, resolution.steps, leaf))
    }


    /** The user-facing kind of a type, for errors and the picker. */
    fun describe(type: DataType): String =
        when (type) {
            is DataType.Scalar -> "a ${type.kind.renderName()} scalar"
            is DataType.Record -> "a record"
            is DataType.Listing -> "a list"
            is DataType.Mapping -> "a map"
            is DataType.Union -> "a union"
            is DataType.Reference -> "a reference to ${type.id.value}"
            is DataType.Opaque -> "an opaque value"
            is DataType.Dynamic -> "a dynamic value"
        }
}
