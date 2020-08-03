package tech.kzen.auto.common.objects.document.filter

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object FilterConventions {
    val processObjectName = ObjectName("Process")
    val archetypeObjectName = ObjectName("Filter")
    val criteriaAttributeName = AttributeName("criteria")
    val criteriaAttributePath = AttributePath.ofName(criteriaAttributeName)

    const val columnKey = "column"
    private const val inputKey = "input"
    private const val outputKey = "output"

    const val nameKey = "name"
    const val progressKey = "progress"

    val inputAttribute = AttributeName(inputKey)
    val outputAttribute = AttributeName(outputKey)

    const val actionParameter = "action"
    const val actionListFiles = "files"
    const val actionListColumns = "columns"
    const val actionLookupOutput = "output"
    const val actionSummaryLookup = "summary-lookup"
    const val actionSummaryTask = "summary-run"
    const val actionFilterTask = "filter"


    fun isFilter(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == archetypeObjectName.value ||
                mainObjectIs == processObjectName.value
    }


    fun getInput(documentNotation: DocumentNotation): String {
        val mainObjectNotation = documentNotation.objects.notations[NotationConventions.mainObjectPath]!!
        return mainObjectNotation.get(inputAttribute)?.asString() ?: ""
    }
}