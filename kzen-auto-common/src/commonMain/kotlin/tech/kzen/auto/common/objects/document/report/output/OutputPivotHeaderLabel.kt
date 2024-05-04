package tech.kzen.auto.common.objects.document.report.output

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType


data class OutputPivotHeaderLabel(
    val headerLabel: HeaderLabel,
    val pivotValueType: PivotValueType?
) {
    fun render(): String {
        return when {
            pivotValueType == null ->
                headerLabel.render()

            else ->
                "${headerLabel.render()} - $pivotValueType"
        }
    }
}