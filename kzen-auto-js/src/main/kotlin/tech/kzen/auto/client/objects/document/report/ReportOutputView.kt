package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.boxShadowInset
import react.*
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.report.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportSaveAction
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


class ReportOutputView(
    props: Props
):
    RPureComponent<ReportOutputView.Props, ReportOutputView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: RState {
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
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
                marginTop = 5.px
            }

//            child(TopIngress::class) {
//                attrs {
//                    ingressColor = Color.white
//                    parentWidth = 100.pct
//                }
//            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(0.5.em)
                    }

                    renderContent()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        renderHeader()

//        +"props.reportState.columnListing: ${props.reportState.columnListing}"
        if (! props.reportState.columnListing.isNullOrEmpty()) {
            renderOutput()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            child(PageviewIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.75.em
                        marginRight = 0.25.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Output"
            }

            styledSpan {
                css {
                    marginLeft = 1.em
                }

                val status = props.reportState.outputInfo?.status ?: OutputStatus.Missing
                if (status == OutputStatus.Missing) {
                    +"Run using play button (bottom right)"
                }
                else {
                    +status.name
                }
            }

            styledSpan {
                css {
                    float = Float.right
                }

                renderHeaderControls()
            }
        }
    }


    private fun RBuilder.renderHeaderControls() {
        val showRefresh = props.reportState.isTaskRunning()
        val showSave = props.reportState.outputInfo?.modifiedTime != null

        if (showRefresh) {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        onPreviewRefresh()
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
                }

                attrs {
                    href = linkAddress
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"
//                        style = reactStyle {
//                            textDecoration = TextDecoration.none
//                        }
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

//            if (! state.savingOpen) {
                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"

                        onClick = {
                            onSaveToggle()
                        }

                        style = reactStyle {
                            marginLeft = 1.em

                            if (state.savingOpen) {
                                backgroundColor = Color.darkGray
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
//            }
        }

        child(MaterialButton::class) {
            attrs {
                title = "Settings"
                variant = "outlined"
                size = "small"

                onClick = {
                    onSettingsToggle()
                }

                style = reactStyle {
                    marginLeft = 1.em

                    if (state.settingsOpen) {
                        backgroundColor = Color.darkGray
                    }
                }
            }

            child(SettingsIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.reportState.outputError
        val outputInfo = props.reportState.outputInfo
        val outputPreview = outputInfo?.preview

        styledDiv {
//            +"outputPreview $outputPreview"

            renderInfo(error, outputInfo)
            renderSettings(outputInfo)
            renderSave(outputInfo)

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
                    attributePath = ReportConventions.workDirPath

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
    private fun RBuilder.renderSave(outputInfo: OutputInfo?) {
        if (! state.savingOpen || outputInfo?.modifiedTime == null) {
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

            styledDiv {
                css {
                    width = 100.pct.minus(11.em)
                    display = Display.inlineBlock
                }

                child(AttributePathValueEditor::class) {
                    attrs {
                        labelOverride = "Save File Path"

                        clientState = props.reportState.clientState
                        objectLocation = props.reportState.mainLocation
                        attributePath = ReportConventions.saveFilePath

                        valueType = TypeMetadata.string

                        onChange = {
                            onPreviewRefresh()
                        }
                    }

                    key = "save-file"
                }

                +outputInfo.saveMessage
            }

            styledDiv {
                css {
                    float = Float.right
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"

                        onClick = {
//                            console.log("^$%^$%^$%^ writing")
                            onSaveAction()
                        }
                    }

                    if (state.savingLoading) {
                        child(MaterialCircularProgress::class) {}
                    }
                    else {
                        child(SaveIcon::class) {
                            attrs {
                                style = reactStyle {
                                    marginRight = 0.25.em
                                }
                            }
                        }
                    }

                    +"Write"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview(outputInfo: OutputInfo, outputPreview: OutputPreview) {
        renderPreviewHeader(outputInfo)
        renderPreviewTable(outputPreview)
    }


    private fun RBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
        if (outputInfo.modifiedTime == null) {
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
                        attributePath = ReportConventions.previewStartPath

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
                        attributePath = ReportConventions.previewCountPath

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
                borderWidth = 1.px
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

                        for (header in outputPreview.header) {
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