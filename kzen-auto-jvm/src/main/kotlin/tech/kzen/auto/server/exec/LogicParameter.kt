package tech.kzen.auto.server.exec

import tech.kzen.auto.common.objects.document.logic.ParameterDefaultDefiner
import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.common.objects.document.logic.TypeMetadataDefiner
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.binding.DataDefault
import tech.kzen.lib.common.exec.data.binding.DataPresence
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
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
    val name: BindingName,
    val type: TypeMetadata,
    val default: Any?
) {
    companion object {
        private val typeAttributePath = AttributePath.ofName(AttributeName("type"))

        fun of(
            location: ObjectLocation,
            graphNotation: GraphNotation,
            objectStableMapper: ObjectStableMapper
        ): LogicParameter {
            val type = graphNotation.firstAttribute(location, typeAttributePath)
                ?.let(TypeMetadataDefiner::parse)
                ?: TypeMetadata.anyNullable
            return LogicParameter(
                objectStableMapper.objectStableId(location),
                BindingName(location.objectPath.name.value),
                type,
                ParameterDefaultDefiner.resolve(location, graphNotation))
        }
    }


    fun definition(): BindingDefinition {
        val contract = BindingSignatureDefiner.contract(type)
        val presence = default?.let { value ->
            val snapshot = DataSnapshot.capture(LiteralDataValues.lift(value, contract))
            val complete = snapshot as? SnapshotResult.Complete
                ?: return@let DataPresence.Optional
            DataPresence.Defaulted(DataDefault(complete.snapshot))
        } ?: DataPresence.Optional
        return BindingDefinition(name, contract, presence)
    }


    fun resolve(inputs: DataBindings): Any? =
        when (val state = inputs[name]) {
            BindingState.Unbound -> null
            is BindingState.Bound -> JobDataValues.boundary(state.value)
        }


    fun resolveValue(inputs: DataBindings): DataValue? =
        when (val state = inputs[name]) {
            BindingState.Unbound -> null
            is BindingState.Bound -> state.value
        }
}
