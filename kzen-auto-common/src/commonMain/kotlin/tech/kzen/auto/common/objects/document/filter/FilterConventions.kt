package tech.kzen.auto.common.objects.document.filter

import tech.kzen.lib.common.model.attribute.AttributeName


object FilterConventions {
    val criteriaAttributeName = AttributeName("criteria")

    const val columnKey = "column"
    const val inputKey = "input"
    const val outputKey = "output"

    val inputAttribute = AttributeName(inputKey)
    val outputAttribute = AttributeName(outputKey)

    const val actionParameter = "action"
    const val actionFiles = "files"
    const val actionColumns = "columns"
    const val actionSummary = "summary"
    const val actionApply = "apply"
}