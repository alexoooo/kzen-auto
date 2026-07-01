package tech.kzen.auto.server.objects.flow

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.LogicDocument
import tech.kzen.auto.server.exec.flow.FlowLogicCompiler
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Flow document (`is: Flow`). The vertex DAG, its input/output vertices, and the
 * derived signature are read straight from notation by [FlowLogicCompiler] when the run is compiled, so this
 * class carries no execution behaviour of its own — it only names the flavour and, via [LogicDocument], routes
 * compilation to the Flow compiler.
 */
@Reflect
class FlowDocument: DocumentArchetype(), LogicDocument {
    override fun toLogic(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic {
        return FlowLogicCompiler.compile(location, graphNotation, graphDefinition, services)
    }
}
