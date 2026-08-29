package tech.kzen.auto.server.objects.job.value

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue


internal data class CalculatedFieldValue(
    val field: FieldId,
    val type: DataType.Scalar,
    val value: ScalarExecutionValue
)


internal data class CarriedField(
    val source: FieldId,
    val rename: FieldId? = null
)


internal sealed interface CarrySelection {
    data object None: CarrySelection
    data class All(val renames: Map<FieldId, FieldId> = emptyMap()): CarrySelection
    data class Selected(val fields: List<CarriedField>): CarrySelection
}


internal data class FormulaTransformResult(
    val value: DataValue,
    val projectionCount: Int,
    val appendCount: Int
)


/**
 * Formula/value transformation over one original projection. Both evaluators run before any append, so neither
 * can observe same-worker calculated fields.
 */
internal object FormulaValueTransformer {
    fun transform(
        claim: JobValueClaim,
        calculate: (ColumnProjection) -> List<CalculatedFieldValue>,
        replace: ((ColumnProjection) -> DataValue)? = null,
        carry: CarrySelection = CarrySelection.None
    ): FormulaTransformResult {
        val originalProjection = JobDataValues.projection(claim.value)
        val calculated = calculate(originalProjection)
        val replacement = replace?.invoke(originalProjection)

        if (replacement == null) {
            val builder = RecordOutputBuilder.open(claim, originalProjection.descriptor)
            appendCalculated(builder, calculated)
            return FormulaTransformResult(
                builder.finish(), builder.projectionCount, builder.appendCount)
        }

        if (carry == CarrySelection.None) {
            return FormulaTransformResult(replacement, 0, 0)
        }

        val widenedBuilder = RecordOutputBuilder.open(claim, originalProjection.descriptor)
        appendCalculated(widenedBuilder, calculated)
        val widened = widenedBuilder.finish()
        val carryProjection = JobDataValues.projection(widened)

        val replacementProjection = JobDataValues.projection(replacement)
        val outputBuilder = RecordOutputBuilder.open(
            JobValueClaim(replacement, exclusive = true), replacementProjection.descriptor)
        val carried = resolveCarry(carry, carryProjection)
        for ((index, rename) in carried) {
            outputBuilder.appendFrom(carryProjection, index, rename)
        }
        return FormulaTransformResult(
            outputBuilder.finish(),
            widenedBuilder.projectionCount + outputBuilder.projectionCount,
            widenedBuilder.appendCount + outputBuilder.appendCount)
    }

    private fun appendCalculated(
        builder: RecordOutputBuilder,
        calculated: List<CalculatedFieldValue>
    ) {
        for (field in calculated) {
            builder.append(field.field, field.type, DataState.Present, field.value)
        }
    }

    private fun resolveCarry(
        selection: CarrySelection,
        projection: ColumnProjection
    ): List<Pair<Int, FieldId?>> =
        when (selection) {
            CarrySelection.None -> emptyList()
            is CarrySelection.All -> (0 until projection.size).map { index ->
                index to selection.renames[projection.field(index)]
            }
            is CarrySelection.Selected -> {
                val selected = selection.fields.associateBy { it.source }
                require(selected.size == selection.fields.size) { "Carry fields must be unique" }
                val available = (0 until projection.size).associateBy { projection.field(it) }
                val unknown = selected.keys - available.keys
                require(unknown.isEmpty()) { "Unknown carry fields: $unknown" }
                (0 until projection.size).mapNotNull { index ->
                    selected[projection.field(index)]?.let { index to it.rename }
                }
            }
        }
}
