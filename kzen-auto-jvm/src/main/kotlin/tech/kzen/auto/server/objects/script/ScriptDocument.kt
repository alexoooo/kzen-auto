package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Script document (`is: Script`) — the notation archetype only. A Script's
 * structure (steps, parameters, results) is read straight from notation by the engine-side
 * [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] when the run is compiled, so this class carries
 * no execution behaviour of its own.
 */
@Reflect
class ScriptDocument: DocumentArchetype()
