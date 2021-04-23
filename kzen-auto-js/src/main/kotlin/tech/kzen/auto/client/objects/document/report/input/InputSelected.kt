package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.title
import react.*
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.InputsSelectionRemoveRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.FolderOpenIcon
import tech.kzen.auto.client.wrap.MaterialButton
import tech.kzen.auto.client.wrap.RemoveCircleOutlineIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.InputDataInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.objects.document.report.progress.ReportProgress
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation


class InputSelected(
    props: Props
):
    RPureComponent<InputSelected.Props, InputSelected.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
        var browserOpen: Boolean
    }


    interface State: RState {
//        var selectedOpen: Boolean
        var showFolders: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        selectedOpen = false
        showFolders = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemoveAll() {
        if (props.editDisabled) {
            return
        }

        val inputSelection = props.reportState.inputSelection
            ?: return

        val inputSelectionSpecs = dataLocationToSpec(
            inputSelection.locations.map { it.dataLocationInfo.path })

        props.dispatcher.dispatchAsync(
            InputsSelectionRemoveRequest(
                inputSelectionSpecs))
    }


    private fun onRemove(path: DataLocation) {
        if (props.editDisabled) {
            return
        }

        val inputSelectionSpecs = dataLocationToSpec(listOf(path))

        props.dispatcher.dispatchAsync(
            InputsSelectionRemoveRequest(
                inputSelectionSpecs))
    }


//    private fun onToggleSelected() {
//        setState {
//            selectedOpen = ! selectedOpen
//        }
//    }


    private fun onToggleFolders() {
        setState {
            showFolders = ! showFolders
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun summaryText(inputSelectionInfo: InputSelectionInfo): String {
        val selected = inputSelectionInfo.locations.map { it.dataLocationInfo }

        val folderCount = selected.map { it.path.parent() }.toSet().size
        val totalSize = selected.sumOf { it.size }

        val filesPlural = if (selected.size == 1) { "file" } else { "files" }
        val foldersText = if (folderCount == 1) { "" } else { "from $folderCount folders " }
        val totalPrefix = if (selected.size == 1) { "" } else { "total " }

        return "${selected.size} $filesPlural ${foldersText}($totalPrefix${FormatUtils.readableFileSize(totalSize)})"
    }


    private fun dataLocationToSpec(dataLocations: List<DataLocation>): List<InputDataSpec> {
        val dataLocationsSet = dataLocations.toSet()
        val inputSelectionSpec = props.reportState.inputSpec().selection
        return inputSelectionSpec.locations.filter { it.location in dataLocationsSet }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val fileListing = props.reportState.inputSelection

        if (fileListing == null || fileListing.isEmpty()) {
            return
        }

        styledDiv {
            if (props.browserOpen) {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    marginTop = 1.em
                }
            }

            if (props.browserOpen) {
                styledDiv {
                    css {
                        width = 100.pct
                    }

                    styledSpan {
                        css {
                            fontSize = 1.5.em
                        }
                        +"Selected"
//                        +"Dataset"
                    }

//                    styledSpan {
//                        css {
//                            float = Float.right
//                        }
//
//                        renderOptions()
//                    }
                }
            }

            val reportProgress = props.reportState.reportProgress

            styledDiv {
                renderDetail(fileListing, reportProgress)
            }
        }
    }


    private fun RBuilder.renderDetail(selected: InputSelectionInfo, reportProgress: ReportProgress?) {
        renderControls(selected)

//        styledDiv {
//            css {
//                marginBottom = 0.25.em
//            }
//            +summaryText(selected)
//        }

        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 1.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
                marginTop = 0.5.em
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                styledThead {
                    styledTr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                width = 2.em
                                height = 2.em
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            attrs {
                                title =
                                    if (props.editDisabled) {
                                        "Can't remove while running"
                                    }
                                    else {
                                        "Remove all"
                                    }
                            }
                            child(RemoveCircleOutlineIcon::class) {
                                attrs {
                                    style = reactStyle {
                                        if (props.editDisabled) {
                                            color = Color.lightGray
                                        }
                                        else {
                                            cursor = Cursor.pointer
                                        }
                                    }

                                    onClick = {
                                        onRemoveAll()
                                    }
                                }
                            }
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                width = 100.pct
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"File"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Modified"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                paddingRight = 0.5.em
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Size"
                        }
                        if (state.showFolders) {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color.white
                                    zIndex = 999
                                    width = 100.pct
                                    textAlign = TextAlign.left
                                    boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                                }
                                +"Folder"
                            }
                        }
                    }
                }

                styledTbody {
                    css {
                        cursor = Cursor.default
                    }

                    for (fileInfo in selected.locations) {
                        renderDetailRow(fileInfo, reportProgress)
                    }
                }
            }
        }
    }


    private fun RBuilder.renderControls(selected: InputSelectionInfo) {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
//                        onRemoveFromSelection()
                    }

//                    if (selectedRemoveCount == 0) {
//                        disabled = true
//                        title =
//                            if (state.selected.isEmpty()) {
//                                "No files selected"
//                            }
//                            else {
//                                "No existing files selected"
//                            }
//                    }
//                    else if (props.editDisabled) {
//                        disabled = true
//                        title = "Disabled while running"
//                    }
                }

                child(RemoveCircleOutlineIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }

//                if (selectedRemoveCount == 0) {
                +"Remove"
//                }
//                else {
//                    +"Remove ($selectedRemoveCount files)"
//                }
            }


            val dataType = props.reportState.inputSpec().selection.dataType
            val dataTypeLabel = dataType.get().substringAfterLast(".")
            +"[Type: $dataTypeLabel]"

            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        onToggleFolders()
                    }

                    style = reactStyle {
                        if (state.showFolders) {
                            backgroundColor = Color.darkGray
                        }

                        paddingLeft = 0.px
                        paddingRight = 0.px
                    }

                    title =
                        if (state.showFolders) {
                            "Hide folders"
                        }
                        else {
                            "Show folders"
                        }
                }

                child(FolderOpenIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderDetailRow(inputDataInfo: InputDataInfo, reportProgress: ReportProgress?) {
        val fileInfo = inputDataInfo.dataLocationInfo
        val fileProgress = reportProgress?.inputs?.get(fileInfo.path)

        styledTr {
            key = fileInfo.path.asString()

            css {
                hover {
                    backgroundColor = InputBrowser.hoverRow
                }
            }

            styledTd {
                styledDiv {
                    css {
                        height = 1.em
                        overflow = Overflow.hidden
                    }
                    attrs {
                        title =
                            if (props.editDisabled) {
                                "Can't remove while running"
                            }
                            else {
                                "Remove"
                            }
                    }
                    child(RemoveCircleOutlineIcon::class) {
                        attrs {
                            style = reactStyle {
                                marginLeft = 0.2.em
                                fontSize = 1.em
                                if (props.editDisabled) {
                                    color = Color.lightGray
                                }
                                else {
                                    cursor = Cursor.pointer
                                }
                            }

                            onClick = {
                                onRemove(fileInfo.path)
                            }
                        }
                    }
                }
            }

            td {
                if (fileProgress == null) {
                    +fileInfo.name
                }
                else {
                    styledDiv {
                        css {
                            if (fileProgress.running) {
                                fontWeight = FontWeight.bold
                            }
                            else if (fileProgress.finished) {
                                color = Color.darkGreen
                            }
                        }
                        +fileInfo.name
                    }
                    styledDiv {
                        css {
                            fontStyle = FontStyle.italic
                            marginBottom = 0.25.em
                        }
                        +fileProgress.toMessage(fileInfo.size)
                    }
                }
            }

            styledTd {
                css {
                    paddingLeft = 0.5.em
                    paddingRight = 0.5.em
                    whiteSpace = WhiteSpace.nowrap
                }
                +FormatUtils.formatLocalDateTime(fileInfo.modified)
            }

            styledTd {
                css {
                    paddingRight = 0.5.em
                    textAlign = TextAlign.right
                    whiteSpace = WhiteSpace.nowrap
                }
                +FormatUtils.readableFileSize(fileInfo.size)
            }

            if (state.showFolders) {
                styledTd {
                    css {
                        paddingRight = 0.5.em
                    }

//                    +fileInfo.path.asUri().substring(0, fileInfo.path.asUri().length - fileInfo.name.length)
                    +fileInfo.path.parent()!!.asString()
                }
            }
        }
    }
}