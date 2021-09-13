package tech.kzen.auto.client.objects.document.report.output

import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.boxShadowInset
import react.*
import react.dom.attrs
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportSaveAction
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.CloudDownloadIcon
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.material.SaveIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


class OutputTableView(
    props: Props
):
    RPureComponent<OutputTableView.Props, OutputTableView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: react.State {
        var settingsOpen: Boolean
        var savingOpen: Boolean
        var savingLoading: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        settingsOpen = ! props.reportState.outputSpec().isDefaultWorkPath()
        savingOpen = false
        savingLoading = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onPreviewRefresh() {
        props.dispatcher.dispatchAsync(OutputLookupRequest)
    }


    private fun abbreviate(value: String): String {
//        if (value.isEmpty()) {
//            // NB: get cell to show
//            return "."
//        }

        if (value.length < 50) {
            return value
        }

//        console.log("^^^^ abbreviating: $value")
        return value.substring(0, 47) + "..."
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSettingsToggle() {
        setState {
            settingsOpen = ! settingsOpen
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSaveToggle() {
        setState {
            savingOpen = ! savingOpen
        }
    }


    private fun onSaveAction() {
        if (state.savingLoading) {
            return
        }

        setState {
            savingLoading = true
        }

        async {
            props.dispatcher.dispatch(
                ReportSaveAction)

            setState {
                savingLoading = false
            }

            onPreviewRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                float = Float.right
            }
            renderHeaderControls()
        }

//        +"props.reportState.columnListing: ${props.reportState.columnListing}"
        if (! props.reportState.columnListing.isNullOrEmpty()) {
            renderOutput()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeaderControls() {
        val showRefresh = props.reportState.isTaskRunning()
        val showSave = (props.reportState.outputInfo?.status ?: OutputStatus.Missing) != OutputStatus.Missing

        if (showRefresh) {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        onPreviewRefresh()
                    }

                    style = reactStyle {
                        borderWidth = 2.px
                        marginLeft = 0.5.em
                    }
                }

                child(RefreshIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }
                +"Refresh"
            }
        }
        else if (showSave) {
            val linkAddress = ClientContext.restClient.linkDetachedDownload(
                props.reportState.mainLocation)

            styledA {
                css {
                    textDecoration = TextDecoration.none
                    marginLeft = 0.5.em
                }

                attrs {
                    href = linkAddress
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"
                        style = reactStyle {
                            borderWidth = 2.px
                        }
                    }

                    child(CloudDownloadIcon::class) {
                        attrs {
                            style = reactStyle {
                                marginRight = 0.25.em
                            }
                        }
                    }

                    +"Download"
                }
            }

            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        onSaveToggle()
                    }

                    style = reactStyle {
                        marginLeft = 1.em
                        borderWidth = 2.px

                        if (state.savingOpen) {
                            backgroundColor = ReportController.selectedColor
                        }
                    }
                }

                child(SaveIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }

                +"Save"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.reportState.outputError
        val outputInfo = props.reportState.outputInfo
        val outputPreview = outputInfo?.table?.preview

        styledDiv {
//            +"outputPreview $outputPreview"

            renderInfo(error, outputInfo)
            renderSettings(outputInfo)
//            renderSave(outputInfo)

            if (outputPreview != null) {
                renderPreview(outputInfo, outputPreview)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInfo(error: String?, outputInfo: OutputInfo?) {
        if (error != null) {
            styledDiv {
                css {
                    marginBottom = 1.em
                    width = 100.pct
                    paddingBottom = 1.em
                    borderBottomWidth = 2.px
                    borderBottomStyle = BorderStyle.solid
                    borderBottomColor = Color.lightGray
                }
                +"Error: $error"
            }
        }
        else {
            styledDiv {
                if (outputInfo == null) {
                    +"..."
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSettings(outputInfo: OutputInfo?) {
        if (! state.settingsOpen || outputInfo == null) {
            return
        }

        styledDiv {
            css {
                marginBottom = 1.em
                width = 100.pct
                paddingBottom = 1.em
                borderBottomWidth = 2.px
                borderBottomStyle = BorderStyle.solid
                borderBottomColor = Color.lightGray
            }

            child(AttributePathValueEditor::class) {
                attrs {
                    labelOverride = "Report Work Folder"

                    clientState = props.reportState.clientState
                    objectLocation = props.reportState.mainLocation
                    attributePath = OutputSpec.workDirPath

                    valueType = TypeMetadata.string

                    onChange = {
                        onPreviewRefresh()
                    }
                }
            }

            +"Absolute path: ${outputInfo.runDir}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderSave(outputInfo: OutputInfo?) {
//        if (! state.savingOpen || outputInfo == null || outputInfo.status == OutputStatus.Missing) {
//            return
//        }
//
//        styledDiv {
//            css {
//                marginBottom = 1.em
//                width = 100.pct
//
//                paddingBottom = 1.em
//                borderBottomWidth = 2.px
//                borderBottomStyle = BorderStyle.solid
//                borderBottomColor = Color.lightGray
//
////                paddingTop = 1.em
////                borderTopWidth = 2.px
////                borderTopStyle = BorderStyle.solid
////                borderTopColor = Color.lightGray
//            }
//
//            styledDiv {
//                css {
//                    width = 100.pct.minus(11.em)
//                    display = Display.inlineBlock
//                }
//
//                child(AttributePathValueEditor::class) {
//                    attrs {
//                        labelOverride = "Save File Path"
//
//                        clientState = props.reportState.clientState
//                        objectLocation = props.reportState.mainLocation
//                        attributePath = ReportConventions.saveFilePath
//
//                        valueType = TypeMetadata.string
//
//                        onChange = {
//                            onPreviewRefresh()
//                        }
//                    }
//
//                    key = "save-file"
//                }
//
//                +(outputInfo.table?.saveMessage ?: "")
//            }
//
//            styledDiv {
//                css {
//                    float = Float.right
//                }
//
//                child(MaterialButton::class) {
//                    attrs {
//                        variant = "outlined"
//                        size = "small"
//
//                        onClick = {
////                            console.log("^$%^$%^$%^ writing")
//                            onSaveAction()
//                        }
//
//                        style = reactStyle {
//                            borderWidth = 2.px
//                        }
//                    }
//
//                    if (state.savingLoading) {
//                        child(MaterialCircularProgress::class) {}
//                    }
//                    else {
//                        child(SaveIcon::class) {
//                            attrs {
//                                style = reactStyle {
//                                    marginRight = 0.25.em
//                                }
//                            }
//                        }
//                    }
//
//                    +"Write"
//                }
//            }
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview(outputInfo: OutputInfo, outputPreview: OutputPreview) {
        renderPreviewHeader(outputInfo)
        renderPreviewTable(outputPreview)
    }


    private fun RBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
        if (outputInfo.status == OutputStatus.Missing) {
            return
        }

        styledDiv {
            css {
                width = 100.pct
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                }

                child(AttributePathValueEditor::class) {
                    attrs {
                        labelOverride = "Preview Start Row"

                        clientState = props.reportState.clientState
                        objectLocation = props.reportState.mainLocation
                        attributePath = OutputExploreSpec.previewStartPath

                        valueType = TypeMetadata.long

                        onChange = {
                            onPreviewRefresh()
                        }
                    }

                    key = "start-row"
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginLeft = 1.em
                }

                child(AttributePathValueEditor::class) {
                    attrs {
                        labelOverride = "Preview Row Count"

                        clientState = props.reportState.clientState
                        objectLocation = props.reportState.mainLocation
                        attributePath = OutputExploreSpec.previewCountPath

                        valueType = TypeMetadata.int

                        onChange = {
                            onPreviewRefresh()
                        }
                    }

                    key = "row-count"
                }
            }

            styledDiv {
                css {
                    float = Float.right
                }

                styledDiv {
                    css {
                        display = Display.inlineBlock
                        marginLeft = 1.em
                    }

                    +"Total rows: ${FormatUtils.decimalSeparator(props.reportState.outputCount())}"
                }
            }
        }
    }


    private fun RBuilder.renderPreviewTable(outputPreview: OutputPreview) {
        styledDiv {
            css {
                maxHeight = 25.em
                overflowY = Overflow.auto
                marginTop = 1.em
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    minWidth = 100.pct
                }

                thead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                left = 0.px
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 1000
                                width = 2.em
                                height = 2.em
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, (-2).px, (-2).px, 0.px, 0.px)
//                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                                boxShadow(Color.lightGray, 2.px, 2.px, 2.px, 0.px)
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Row Number"
                        }

                        for (header in outputPreview.header.values) {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
//                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    backgroundColor = Color.white
                                    zIndex = 999
//                                    boxShadow(Color.lightGray, 0.px, 2.px, 2.px, 0.px)
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    textAlign = TextAlign.left
                                    boxShadowInset(Color.lightGray, 0.px, (-2).px, 0.px, 0.px)
                                }
                                key = header
                                +header
                            }
                        }
                    }
                }

                tbody {
                    for (row in outputPreview.rows.withIndex()) {
                        styledTr {
                            key = row.index.toString()

                            css {
//                                backgroundColor = Color.white
                                hover {
                                    backgroundColor = Color.lightGrey
                                }
                            }

                            styledTd {
                                css {
                                    position = Position.sticky
                                    left = 0.px
//                                    top = 0.px
                                    backgroundColor = Color.white
                                    zIndex = 999

//                                    borderTopStyle = BorderStyle.solid
//                                    borderTopColor = Color.lightGray
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    boxShadowInset(Color.lightGray, (-2).px, 0.px, 0.px, 0.px)
//                                    boxShadow(Color.lightGray, 2.px, 2.px, 2.px, 0.px)

                                    if (row.index != 0) {
                                        borderTopWidth = 1.px
                                        borderTopStyle = BorderStyle.solid
                                        borderTopColor = Color.lightGray
                                    }
                                }
                                val rowNumber = row.index + outputPreview.startRow.coerceAtLeast(0)
                                val rowFormat = FormatUtils.decimalSeparator(rowNumber + 1)
                                +rowFormat
                            }

                            for (value in row.value.withIndex()) {
                                styledTd {
                                    css {
//                                        borderTopStyle = BorderStyle.solid
//                                        borderTopColor = Color.lightGray
                                        paddingLeft = 0.5.em
                                        paddingRight = 0.5.em

                                        if (row.index != 0) {
                                            borderTopWidth = 1.px
                                            borderTopStyle = BorderStyle.solid
                                            borderTopColor = Color.lightGray
                                        }
                                    }

                                    key = value.index.toString()
                                    +abbreviate(value.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}