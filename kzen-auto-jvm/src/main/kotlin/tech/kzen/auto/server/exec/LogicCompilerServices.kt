package tech.kzen.auto.server.exec

import tech.kzen.auto.server.objects.job.JobValidationCache
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.objects.script.ScriptValidationCache
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The runtime services a [LogicCompiler] (and the per-flavour compilers it delegates to) needs to translate
 * a notation document into an engine [tech.kzen.lib.common.exec.engine.Logic]. Bundled because they travel
 * together through the Script / Flow / Job / Report compilers — and a [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep]
 * or [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex] can nest another flavour, so
 * each compiler must be able to compile an arbitrary child via [LogicCompiler] without re-threading args.
 *
 * [scriptValidationCache] makes repeated Script validation free when the relevant notation is unchanged
 * (shared with the editor's detached validation path, so run compiles and editor requests reuse entries);
 * [jobValidationCache] is its Job analogue (the payload-type walk [tech.kzen.auto.server.exec.job.JobRun]
 * threads into each Worker's control, shared with the editor's detached JobValidator).
 * [notationMetadataReader] backs Job's
 * [tech.kzen.auto.common.objects.document.job.JobChannelSynthesis] (order-driven channel augmentation).
 * [jobWorkPool] owns the per-run scratch directories a Job's file-backed Workers (Pivot / Explore) resolve via
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.scratchDir].
 * [runExecutionId] is the identity of the run being compiled for, used by flavours that persist run artifacts
 * keyed to the run (Report stamps its run dir with it, for offline trace correlation; Job keys each Worker's
 * scratch dir on its migrate-stable run id); it stays the same across a live-edit recompile / migrate of one run.
 * [tech.kzen.lib.common.service.context.GraphCreator] is not here: it is a stateless object, used directly.
 */
class LogicCompilerServices(
    val graphEnvironment: GraphEnvironment,
    val objectStableMapper: ObjectStableMapper,
    val cachedKotlinCompiler: CachedKotlinCompiler,
    val scriptValidationCache: ScriptValidationCache,
    val jobValidationCache: JobValidationCache,
    val notationMetadataReader: NotationMetadataReader,
    val jobWorkPool: JobWorkPool,
    val runExecutionId: LogicRunExecutionId
)
