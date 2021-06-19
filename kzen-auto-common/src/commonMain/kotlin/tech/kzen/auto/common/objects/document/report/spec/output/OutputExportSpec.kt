package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


data class OutputExportSpec(
    val pathExpression: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofNotation(attributeNotation: ScalarAttributeNotation): OutputExportSpec {
            val pathExpression = attributeNotation.value
            return OutputExportSpec(pathExpression)
        }
    }
}