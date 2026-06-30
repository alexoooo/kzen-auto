package tech.kzen.auto.server.objects.flow

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Flow document (`is: Flow`) — the notation archetype only. The vertex DAG, its
 * input/output vertices, and the derived signature are read straight from notation by the engine-side
 * [tech.kzen.auto.server.exec.flow.FlowLogicCompiler] when the run is compiled, so this class carries no
 * execution behaviour of its own.
 */
@Reflect
class FlowDocument: DocumentArchetype()
