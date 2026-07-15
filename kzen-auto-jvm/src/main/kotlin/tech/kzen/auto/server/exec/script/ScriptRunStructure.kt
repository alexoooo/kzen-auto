package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The immutable, per-Script compilation artefacts a [ScriptRunContext] needs to run the step archetypes: the
 * instantiated graph (to resolve a step location to its `@Reflect` [tech.kzen.auto.server.objects.script.api.ScriptStep]
 * instance), the notation / definition (to host a RunStep's linked child), and the validated Script structures
 * the expression steps type against, and the statically-derived [valueReferencedSteps] a collecting step consults
 * (see [ScriptValueReferences]). Produced once by [ScriptLogicCompiler]; shared by every run of the resulting
 * [ScriptLogic] (a fresh [ScriptRunContext] per run holds the mutable state).
 */
class ScriptRunStructure(
    val scriptLocation: ObjectLocation,
    val graphNotation: GraphNotation,
    val graphDefinition: GraphDefinition,
    val graphInstance: GraphInstance,
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation,
    val resultSignature: TupleDefinition,
    val valueReferencedSteps: Set<ObjectLocation>,
    val services: LogicCompilerServices
) {
    val objectStableMapper: ObjectStableMapper get() = services.objectStableMapper
}
