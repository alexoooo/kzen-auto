package tech.kzen.auto.server.exec

import tech.kzen.auto.common.objects.document.logic.ParameterDefaultDefiner
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * A declared parameter binding, flavour-neutral (Script and Job share the `parameters` branch of typed
 * ParameterBinding declarations): its run value is the input named [name], or the [default] when the run
 * supplies none ([resolve]). Compiled from notation once per compile ([of]); at run start each flavour resolves
 * it against the run's inputs — Script records it under [stableId] so expressions can reference it like any
 * other in-scope value, Job serves it lazily by name — and surfaces the resolved value to the trace via
 * [LogicParameterTrace].
 */
class LogicParameter(
    val stableId: ObjectStableId,
    val name: TupleComponentName,
    val default: Any?
) {
    companion object {
        fun of(
            location: ObjectLocation,
            graphNotation: GraphNotation,
            objectStableMapper: ObjectStableMapper
        ): LogicParameter {
            return LogicParameter(
                objectStableMapper.objectStableId(location),
                TupleComponentName(location.objectPath.name.value),
                ParameterDefaultDefiner.resolve(location, graphNotation))
        }
    }


    fun resolve(inputs: TupleValue): Any? {
        return inputs.find(name) ?: default
    }
}
