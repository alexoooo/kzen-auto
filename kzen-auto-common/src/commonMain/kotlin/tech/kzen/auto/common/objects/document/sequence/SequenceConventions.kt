package tech.kzen.auto.common.objects.document.sequence

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName


object SequenceConventions {
    val objectName = ObjectName("Sequence")

    val stepsAttributeName = AttributeName("steps")
    val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

//    private const val inputKey = "input"
//    val inputAttributeName = AttributeName(inputKey)
//    val inputAttributePath = AttributePath.ofName(inputAttributeName)
//
//    val formulaAttributeName = AttributeName("formula")
//    val formulaAttributePath = AttributePath.ofName(formulaAttributeName)
//
//    val filterAttributeName = AttributeName("filter")
//    val filterAttributePath = AttributePath.ofName(filterAttributeName)
//
//    val previewAllAttributeName = AttributeName("previewAll")
//    val previewFilteredAttributeName = AttributeName("previewFiltered")
//
//    val analysisAttributeName = AttributeName("analysis")
//    val analysisAttributePath = AttributePath.ofName(analysisAttributeName)
//    val pivotAttributePath = analysisAttributePath.nest(AttributeSegment.ofKey("pivot"))
//
//    val outputAttributeName = AttributeName("output")
//    val outputAttributePath = AttributePath.ofName(outputAttributeName)
//
//    const val previewPivotValuesKey = "values"
//
//
//    const val actionParameter = "action"
//    const val actionBrowseFiles = "browse"
//    const val actionInputInfo = "files"
//    const val actionDataTypes = "types"
//    const val actionDefaultFormat = "defaultFormat"
//    const val actionTypeFormats = "typeFormats"
//    const val actionListColumns = "columns"
//    const val actionOutputInfoOffline = "output-offline"
//    const val actionOutputInfoOnline = "output-online"
//    const val actionSummaryOffline = "summary-offline"
//    const val actionSummaryOnline = "summary-online"
//    const val actionReset = "reset"
//    const val actionValidateFormulas = "formulas"
//
//    const val filesParameter = "files"
//
//
//    private val traceInputPrefix = "input"
//
//    val outputTracePath = LogicTracePath(listOf("output"))
//
//
//    fun inputTracePath(dataLocation: DataLocation): LogicTracePath {
//        return LogicTracePath(listOf(
//            traceInputPrefix, dataLocation.digest().asString()))
//    }
}