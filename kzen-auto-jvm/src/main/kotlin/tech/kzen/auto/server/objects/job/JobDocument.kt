package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Job document (`is: Job`) — the notation archetype only. The Workers and the
 * order-derived Channels connecting them are read / synthesized straight from notation by the engine-side
 * [tech.kzen.auto.server.exec.job.JobLogicCompiler] when the run is compiled, so this class carries no
 * execution behaviour of its own.
 */
@Reflect
class JobDocument: DocumentArchetype()
