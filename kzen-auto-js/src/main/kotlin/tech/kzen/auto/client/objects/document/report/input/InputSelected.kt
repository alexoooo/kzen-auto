package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.InputsSelectionRemoveRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.*
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
    }


    interface State: RState {
        var selectedOpen: Boolean
        var showFolders: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        selectedOpen = false
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


    private fun onToggleSelected() {
        setState {
            selectedOpen = ! selectedOpen
        }
    }


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
        val forceOpen = fileListing?.isEmpty() ?: false

        styledDiv {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = BorderStyle.solid
                marginTop = 1.em
            }

            styledDiv {
                css {
                    width = 100.pct
                }

                styledSpan {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Selected"
                }

                if (! forceOpen) {
                    styledSpan {
                        css {
                            float = Float.right
                        }

                        renderOptions()
                    }
                }
            }

            val reportProgress = props.reportState.reportProgress

            styledDiv {
                when {
                    fileListing == null -> {
                        +"Loading..."
                    }

                    fileListing.isEmpty() -> {
                        +"None (please select in Browser above)"
                    }

                    state.selectedOpen -> {
                        renderDetail(fileListing, reportProgress)
                    }

                    else -> {
                        renderSummary(fileListing, reportProgress)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOptions() {
        +"[Data Type]"

        if (state.selectedOpen) {
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

        child(MaterialIconButton::class) {
            attrs {
                onClick = {
                    onToggleSelected()
                }
            }

            if (state.selectedOpen) {
                child(ExpandLessIcon::class) {}
            } else {
                child(ExpandMoreIcon::class) {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSummary(inputSelectionInfo: InputSelectionInfo, reportProgress: ReportProgress?) {
        val runningFile = reportProgress?.inputs?.filter { it.value.running }?.keys?.lastOrNull()

        val selected = inputSelectionInfo.locations

        if (selected.size == 1) {
            styledSpan {
                css {
                    if (runningFile != null) {
                        fontWeight = FontWeight.bold
                    }
                }
                +selected.single().dataLocationInfo.name
            }
            if (runningFile != null) {
                div {
                    +reportProgress.inputs[runningFile]!!.toMessage(
                        selected.single().dataLocationInfo.size)
                }
            }
        }
        else {
            table {
                tbody {
                    tr {
                        td {
                            +"1."
                        }
                        styledTd {
                            +selected[0].dataLocationInfo.name
                        }
                    }

                    if (selected.size == 3) {
                        tr {
                            td {
                                +"2."
                            }
                            styledTd {
                                +selected[1].dataLocationInfo.name
                            }
                        }
                    }
                    else if (selected.size > 3) {
                        tr {
                            styledTd {
                                css {
                                    paddingTop = 0.25.em
                                    paddingBottom = 0.25.em
                                }

                                attrs {
                                    colSpan = "2"
                                }

                                styledSpan {
                                    css {
                                        cursor = Cursor.pointer
                                    }

                                    attrs {
                                        onClickFunction = {
                                            onToggleSelected()
                                        }
                                    }

                                    +"... ${selected.size - 2} more files, expand to view all ..."
                                }
                            }
                        }
                    }

                    tr {
                        td {
                            +"${selected.size}."
                        }
                        styledTd {
                            +selected.last().dataLocationInfo.name
                        }
                    }
                }
            }

            if (runningFile != null) {
                val selectedRunning = selected.first { it.dataLocationInfo.path == runningFile }
                styledDiv {
                    css {
                        marginTop = 0.25.em
                        fontWeight = FontWeight.bold
                    }
                    +"Running: ${selectedRunning.dataLocationInfo.name}"
                }
                div {
                    +reportProgress.inputs[runningFile]!!.toMessage(
                        selectedRunning.dataLocationInfo.size)
                }
            }
        }
    }


    private fun RBuilder.renderDetail(selected: InputSelectionInfo, reportProgress: ReportProgress?) {
        styledDiv {
            css {
                marginBottom = 0.25.em
            }
            +summaryText(selected)
        }

        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 1.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
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