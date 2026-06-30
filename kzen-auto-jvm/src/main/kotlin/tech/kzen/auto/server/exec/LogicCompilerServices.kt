package tech.kzen.auto.server.exec

import tech.kzen.auto.common.paradigm.flow.service.format.FlowMessageInspector
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The runtime services a [LogicCompiler] (and the per-flavour compilers it delegates to) needs to translate
 * a notation document into an engine [tech.kzen.lib.common.exec.engine.Logic]. Bundled because they travel
 * together through the Script / Flow / Job compilers — and a [RunStep][tech.kzen.auto.server.exec.script.step.RunStep]
 * or [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex] can nest another flavour, so
 * each compiler must be able to compile an arbitrary child via [LogicCompiler] without re-threading args.
 *
 * [flowMessageInspector] is Flow's per-vertex message renderer; [notationMetadataReader] backs Job's
 * [tech.kzen.auto.common.objects.document.job.JobChannelSynthesis] (order-driven channel augmentation).
 * [tech.kzen.lib.common.service.context.GraphCreator] is not here: it is a stateless object, used directly.
 */
class LogicCompilerServices(
    val graphEnvironment: GraphEnvironment,
    val objectStableMapper: ObjectStableMapper,
    val cachedKotlinCompiler: CachedKotlinCompiler,
    val flowMessageInspector: FlowMessageInspector,
    val notationMetadataReader: NotationMetadataReader
)
