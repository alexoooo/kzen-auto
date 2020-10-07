package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object ProcessConventions {
    private val processObjectName = ObjectName("Process")

    val filterAttributeName = AttributeName("filter")
    val filterAttributePath = AttributePath.ofName(filterAttributeName)

    val pivotAttributeName = AttributeName("pivot")
    val pivotAttributePath = AttributePath.ofName(pivotAttributeName)

//    const val columnKey = "column"
    private const val inputKey = "input"
    private const val outputKey = "output"

//    const val nameKey = "name"
//    const val progressKey = "progress"

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

//        return mainObjectIs == archetypeObjectName.value ||
//                mainObjectIs == processObjectName.value
        return mainObjectIs == processObjectName.value
    }


//    fun getInput(documentNotation: DocumentNotation): String {
//        val mainObjectNotation = documentNotation.objects.notations[NotationConventions.mainObjectPath]!!
//        return mainObjectNotation.get(inputAttribute)?.asString() ?: ""
//    }
}