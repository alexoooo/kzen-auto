package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.design.DesignReadSession
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType


/**
 * Types a reading Worker from data before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5):
 * the parts of each of [count] values are inspected through a [DesignReadSession], and their shapes merge by the
 * Worker's schema mode exactly as a run's parts do ([DataReadCore.planShape]). Shared by `Parse` (the values its
 * input lane offers) and `Read` (the units of its data source).
 */
internal object DesignShapeInference {
    /** A column of the merged payload, rendered as a header names it, and the inspected parts that hold it. */
    class InferredColumn(
        val name: String,
        val origins: List<String>
    )


    /** [columns] are the merged payload's, each with the parts that hold it, of [parts] inspected. */
    class Inference(
        val payload: DataContract?,
        val read: Int,
        val total: Int,
        val error: String?,
        val columns: List<InferredColumn> = listOf(),
        val parts: Int = 0
    ) {
        /** The lane this inference types: [unknown] with its payload replaced, carrying [unknown]'s metadata. */
        fun attempt(unknown: DataContract): JobLaneAttempt {
            if (error != null) {
                return JobLaneAttempt(JobLaneDescriptor(unknown), error)
            }
            if (payload == null) {
                return JobLaneAttempt(JobLaneDescriptor(unknown), null)
            }
            return JobLaneAttempt(
                JobLaneDescriptor(payload.withMetadata(unknown.metadata)), null,
                provenance = "Inferred from $read of $total values")
        }
    }


    /**
     * [partsOf] gives the parts of value `index`, or null for a value that cannot be looked at ahead of the run
     * (content read once), which is passed over.
     */
    fun infer(
        count: Int,
        design: DesignReadSession,
        openerLookup: DataOpenerLookup,
        schemaMode: String,
        partsOf: suspend (DataContext, Int) -> List<DataPart>?
    ): Inference {
        val candidates = mutableListOf<DataReadCore.ShapeCandidate>()
        var read = 0
        for (index in 0 until count) {
            val inspected =
                try {
                    design.read { context ->
                        partsOf(context, index)?.map { part ->
                            val shape = design.inspectShape(context, openerLookup, part)
                                ?: throw IllegalStateException("Unable to inspect the shape of ${part.ref.display()}")
                            DataReadCore.ShapeCandidate(shape, null, part.ref.display())
                        }
                    }
                }
                catch (e: Exception) {
                    return Inference(
                        null, read, count, "Unable to read value ${index + 1} before Run: ${e.message}")
                }
            val shapes = inspected
                ?: continue
            candidates.addAll(shapes)
            read += 1
        }
        if (candidates.isEmpty()) {
            return Inference(null, read, count, null)
        }
        val planned =
            try {
                DataReadCore.planShape(candidates, schemaMode)
            }
            catch (e: IllegalStateException) {
                return Inference(null, read, count, e.message)
            }
        return Inference(
            planned.shape.itemType, read, count, null, columns(planned.shape.itemType, candidates),
            candidates.size)
    }


    private fun columns(payload: DataContract, candidates: List<DataReadCore.ShapeCandidate>): List<InferredColumn> {
        val fields = recordFields(payload)
            ?: return listOf()
        val held = candidates.map { candidate -> recordFields(candidate.shape.itemType)?.toSet().orEmpty() }
        return fields.map { field ->
            InferredColumn(
                HeaderLabel(field.name, field.occurrence).render(),
                candidates.indices.filter { field in held[it] }.map { candidates[it].origin })
        }
    }


    private fun recordFields(contract: DataContract) =
        (contract.expanded().structural as? DataType.Record)?.fields?.map { it.id }
}
