package tech.kzen.auto.server.exec.job

import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition


/**
 * The declared-parameter seam, compiled once by [JobLogicCompiler] and threaded run-constant to every Worker's
 * [EngineJobControl]: [declarations] is the Job's typed input signature (the `parameters` branch read by
 * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]), and [defaults] the per-parameter typed
 * default values resolved from notation
 * ([tech.kzen.auto.common.objects.document.logic.ParameterDefaultDefiner.resolve]) — the fallback
 * `JobControl.parameter` returns when the run binds no argument (Script parity).
 */
class JobParameters(
    val declarations: TupleDefinition,
    val defaults: Map<TupleComponentName, Any?>
) {
    companion object {
        val empty = JobParameters(TupleDefinition.empty, mapOf())
    }
}
