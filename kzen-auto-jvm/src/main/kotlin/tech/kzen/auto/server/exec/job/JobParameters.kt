package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.exec.LogicParameter
import tech.kzen.lib.common.exec.data.binding.BindingSchema


/**
 * The declared-parameter seam, compiled once by [JobLogicCompiler] and threaded run-constant to every Worker's
 * [EngineJobControl]: [declarations] is the Job's typed input signature (the `parameters` branch read by
 * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]), and [bindings] the per-parameter
 * [LogicParameter] models (stable id + name + typed default resolved from notation) — [JobRun] surfaces each
 * binding's resolved value to the trace at run start, and [defaults] is the fallback `JobControl.parameter`
 * returns when the run binds no argument (Script parity).
 */
class JobParameters(
    val declarations: BindingSchema,
    val bindings: List<LogicParameter>
) {
    companion object {
        val empty = JobParameters(BindingSchema.empty, listOf())
    }
}
