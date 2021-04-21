package tech.kzen.auto.common.objects.document.report

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object ReportConventions {
    private val reportObjectName = ObjectName("Report")

    private const val inputKey = "input"
    val inputAttributeName = AttributeName(inputKey)
    val inputAttributePath = AttributePath.ofName(inputAttributeName)

    val formulaAttributeName = AttributeName("formula")
    val formulaAttributePath = AttributePath.ofName(formulaAttributeName)

    val filterAttributeName = AttributeName("filter")
    val filterAttributePath = AttributePath.ofName(filterAttributeName)

    val pivotAttributeName = AttributeName("pivot")
    val pivotAttributePath = AttributePath.ofName(pivotAttributeName)

    val outputAttributeName = AttributeName("output")
    val outputAttributePath = AttributePath.ofName(outputAttributeName)

    const val workDirKey = "work"
    val workDirPath = outputAttributePath.nest(AttributeSegment.ofKey(workDirKey))

    const val previewStartKey = "start"
    val previewStartPath = outputAttributePath.nest(AttributeSegment.ofKey(previewStartKey))

    const val previewRowCountKey = "count"
    val previewCountPath = outputAttributePath.nest(AttributeSegment.ofKey(previewRowCountKey))

    const val saveFileKey = "save"
    val saveFilePath = outputAttributePath.nest(AttributeSegment.ofKey(saveFileKey))

//    const val previewPivotValuesKey = "values"


    const val actionParameter = "action"
    const val actionBrowseFiles = "browse"
    const val actionInputInfo = "files"
    const val actionDataTypes = "types"
    const val actionDefaultFormat = "defaultFormat"
    const val actionTypeFormats = "typeFormats"
    const val actionListColumns = "columns"
    const val actionLookupOutput = "output"
    const val actionSummaryLookup = "summary-lookup"
    const val actionRunTask = "run"
    const val actionSave = "save"
    const val actionReset = "reset"
    const val actionValidateFormulas = "formulas"

    const val filesParameter = "files"


    fun isReport(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == reportObjectName.value
    }
}