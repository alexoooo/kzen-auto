package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


/**
 * The statically known shape of one Job channel lane — what the payload-type walk
 * ([tech.kzen.auto.server.objects.job.JobValidator]) folds along the wiring, one Worker at a time
 * ([WorkerBase.payloadFlow]):
 *
 * - [payloadType] — the inferred payload type of the [JobMessage]s flowing on this lane; null = no static
 *   payload (a flat/CSV lane, or an untyped source). Downstream expression compiles use it as the receiver
 *   (null → nullable `Any`), and it is what the editor's worker cards display.
 * - [flatColumns] — the flat part's columns, where statically derivable: null = UNKNOWN (a CSV lane's
 *   header only exists at run time); empty = statically NO flat part (a pure-payload lane). Known columns
 *   let the walk compile-check expressions with the exact scope the runtime will use. An unknown lane still
 *   gets the scope-free half of that check — expressions are parsed for syntax
 *   ([tech.kzen.auto.server.service.compile.KotlinSyntaxValidator]), since malformed source cannot compile
 *   under any header; only errors a header would settle (unresolved columns, type mismatches) are left to
 *   surface at run time.
 */
class WorkerLane(
    val payloadType: TypeMetadata?,
    val flatColumns: HeaderListing?
) {
    companion object {
        /** The lane before any Worker has typed it: no payload type, columns unknown. */
        val unknown = WorkerLane(null, null)

        private val mapClassName = ClassName("kotlin.collections.Map")
    }


    /**
     * The flat columns a [JobMessage.flatView] consumer of this lane statically sees: known columns as-is;
     * a pure-payload lane auto-flattens — the shared `value` column for a concrete non-Map payload type,
     * but a `Map` payload (keyed columns from its runtime keys) or an untyped one is unknown. Null =
     * unknown (static expression validation is skipped).
     */
    fun consumerFlatColumns(): HeaderListing? {
        val columns = flatColumns
            ?: return null
        if (columns.values.isNotEmpty()) {
            return columns
        }

        val payloadClassName = payloadType?.className
            ?: return null
        if (payloadClassName == ClassNames.kotlinAny || payloadClassName == mapClassName) {
            return null
        }
        return JobMessage.valueHeader
    }


    /**
     * The static type of [JobMessage.boundaryValue] for this lane — what a boundary worker
     * ([ResultSinkWorker] yield, [RunWorker] child argument) sends across: a typed payload lane crosses as
     * its payload; a statically flat-only lane materializes to an ordered `Map<String, String>`. Null =
     * statically unknown (a CSV lane's payload absence, an untyped source, or a statically empty message) —
     * boundary-type checks are skipped, their errors surfacing at run time as before.
     */
    fun boundaryType(): TypeMetadata? {
        payloadType?.let { return it }

        val columns = flatColumns
            ?: return null
        if (columns.values.isEmpty()) {
            return null
        }
        return TypeMetadata(mapClassName, listOf(TypeMetadata.string, TypeMetadata.string), false)
    }
}


/**
 * One Worker's contribution to the payload-type walk: the OUTPUT [lane] it produces from its input lane,
 * plus an optional validation [errorMessage] (an expression compile error, surfaced on the Worker's card —
 * never a run-time crash).
 */
class WorkerLaneAttempt(
    val lane: WorkerLane,
    val errorMessage: String?
)


/**
 * What a [WorkerBase.payloadFlow] override needs beyond its own injected services: the Job's declared
 * [parameters] (every expression's typed parameter scope), the saved [graphStructure] (a nested-Logic Worker
 * reads its callee's signature from it), and the [classLoader] probe compiles run under.
 */
class WorkerLaneContext(
    val parameters: TupleDefinition,
    val graphStructure: GraphStructure,
    val classLoader: ClassLoader
)
