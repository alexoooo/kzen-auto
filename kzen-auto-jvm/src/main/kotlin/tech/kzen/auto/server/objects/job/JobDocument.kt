package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.LogicDocument
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Job document (`is: Job`). The Workers and the order-derived Channels connecting
 * them are read / synthesized straight from notation by [JobLogicCompiler] when the run is compiled, so this
 * class carries no execution behaviour of its own — it only names the flavour and, via [LogicDocument], routes
 * compilation to the Job compiler.
 */
@Reflect
class JobDocument: DocumentArchetype(), LogicDocument {
    override fun toLogic(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic {
        return JobLogicCompiler.compile(location, graphNotation, graphDefinition, services)
    }
}
