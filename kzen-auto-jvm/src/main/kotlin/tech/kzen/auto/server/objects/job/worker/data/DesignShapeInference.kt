package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.design.DesignReadSession
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.lib.common.exec.data.type.DataContract


/**
 * Types a reading Worker from data before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5):
 * the parts of each of [count] values are inspected through a [DesignReadSession], within its budget, and their
 * shapes merge by the Worker's schema mode exactly as a run's parts do ([DataReadCore.planShape]). Shared by `Parse`
 * (the values its input lane offers) and `Read` (the units of its data source).
 */
internal object DesignShapeInference {
    class Inference(
        val payload: DataContract?,
        val read: Int,
        val total: Int,
        val partial: Boolean,
        val error: String?
    ) {
        /** The lane this inference types: [unknown] with its payload replaced, carrying [unknown]'s metadata. */
        fun attempt(unknown: DataContract): JobLaneAttempt {
            if (error != null) {
                return JobLaneAttempt(JobLaneDescriptor(unknown), error)
            }
            if (payload == null) {
                return JobLaneAttempt(JobLaneDescriptor(unknown), null, partial = partial)
            }
            val provenance = "Inferred from $read of $total values" +
                (if (partial) " (time limit reached; reading on)" else "")
            return JobLaneAttempt(
                JobLaneDescriptor(payload.withMetadata(unknown.metadata)), null,
                provenance = provenance, partial = partial)
        }
    }


    /**
     * [partsOf] gives the parts of value `index`, or null for a value that cannot be looked at ahead of the run
     * (content read once), which is passed over.
     */
    fun infer(
        count: Int,
        total: Int,
        design: DesignReadSession,
        openerLookup: DataOpenerLookup,
        schemaMode: String,
        partsOf: suspend (DataContext, Int) -> List<DataPart>?
    ): Inference {
        val candidates = mutableListOf<DataReadCore.ShapeCandidate>()
        var read = 0
        var partial = false
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
                        null, read, total, false, "Unable to read value ${index + 1} before Run: ${e.message}")
                }
            if (inspected == null) {
                partial = true
                break
            }
            val shapes = inspected.value
                ?: continue
            candidates.addAll(shapes)
            read += 1
        }
        if (candidates.isEmpty()) {
            return Inference(null, read, total, partial, null)
        }
        val planned =
            try {
                DataReadCore.planShape(candidates, schemaMode)
            }
            catch (e: IllegalStateException) {
                return Inference(null, read, total, partial, e.message)
            }
        return Inference(planned.shape.itemType, read, total, partial, null)
    }
}
