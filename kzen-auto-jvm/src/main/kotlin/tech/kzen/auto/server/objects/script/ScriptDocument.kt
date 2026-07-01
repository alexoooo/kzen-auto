package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.LogicDocument
import tech.kzen.auto.server.exec.script.ScriptLogicCompiler
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Script document (`is: Script`). A Script's structure (steps, parameters, results)
 * is read straight from notation by [ScriptLogicCompiler] when the run is compiled, so this class carries no
 * execution behaviour of its own — it only names the flavour and, via [LogicDocument], routes compilation to
 * the Script compiler.
 */
@Reflect
class ScriptDocument: DocumentArchetype(), LogicDocument {
    override fun toLogic(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic {
        return ScriptLogicCompiler.compile(location, graphNotation, graphDefinition, services)
    }
}
