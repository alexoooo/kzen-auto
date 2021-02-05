package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.title
import react.*
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.InputsSelectionRemoveRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.listing.FileInfo
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.common.util.FormatUtils


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
        val inputSelected = props.reportState.inputSelected
            ?: return

        props.dispatcher.dispatchAsync(
            InputsSelectionRemoveRequest(
                inputSelected.map { it.path }))
    }


    private fun onRemove(path: String) {
        props.dispatcher.dispatchAsync(
            InputsSelectionRemoveRequest(
                listOf(path)))
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
    override fun RBuilder.render() {
        val fileListing = props.reportState.inputSelected
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

                                child(FolderOpenIcon::class) {
                                    attrs {
                                        style = reactStyle {
//                                            marginLeft = (-1).em
//                                            marginRight = (-1).em
                                        }
                                    }
                                }
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
                }
            }

            styledDiv {
                when {
                    fileListing == null -> {
                        +"Loading..."
                    }

                    fileListing.isEmpty() -> {
                        +"None (please select in Browser above)"
                    }

                    state.selectedOpen -> {
                        renderDetail(fileListing)
                    }

                    else -> {
                        renderSummary(fileListing)
                    }
                }
            }
        }

        val taskProgress = props.reportState.taskProgress
        if (taskProgress != null) {
            renderProgress(taskProgress)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSummary(selected: List<FileInfo>) {
        when (selected.size) {
            1 -> {
                styledSpan {
                    css {
                        fontFamily = "monospace"
                    }
                    +selected.single().name
                }
            }

            2 -> {
                styledSpan {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        fontFamily = "monospace"
                    }
                    +selected[0].name
                }
                +", "
                styledSpan {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        fontFamily = "monospace"
                    }
                    +selected[1].name
                }
            }

            else -> {
                +"${selected.size} files: "
                styledSpan {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        fontFamily = "monospace"
                    }
                    +selected[0].name
                }
                +", ..., "
                styledSpan {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        fontFamily = "monospace"
                    }
                    +selected[1].name
                }
            }
        }
    }


    private fun RBuilder.renderDetail(selected: List<FileInfo>) {
        styledDiv {
            css {
//                marginTop = 0.1.em
                marginBottom = 0.25.em
            }
            val folderCount = selected.map { it.path.substring(0, it.path.length - it.name.length) }.toSet().size
            val totalSize = selected.map { it.size }.sum()
            +"${selected.size} files from $folderCount folders totalling ${FormatUtils.readableFileSize(totalSize)}"
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
                                title = "Remove all"
                            }
                            child(RemoveCircleOutlineIcon::class) {
                                attrs {
                                    style = reactStyle {
                                        cursor = Cursor.pointer
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

                    for (fileInfo in selected) {
                        styledTr {
                            key = fileInfo.path

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
                                        title = "Remove"
                                    }
                                    child(RemoveCircleOutlineIcon::class) {
                                        attrs {
                                            style = reactStyle {
                                                marginLeft = 0.2.em
                                                fontSize = 1.em
                                                cursor = Cursor.pointer
                                            }

                                            onClick = {
                                                onRemove(fileInfo.path)
                                            }
                                        }
                                    }
                                }
                            }

                            td {
                                +fileInfo.name
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
                                    +fileInfo.path.substring(0, fileInfo.path.length - fileInfo.name.length)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderProgress(taskProgress: TaskProgress) {
        if (! props.reportState.isTaskRunning()) {
            return
        }

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            styledDiv {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px
                }

                when {
                    props.reportState.taskRunning -> {
                        +"Running"
                    }

//                    props.reportState.indexTaskRunning -> {
//                        +"Indexing"
//                    }
                }
            }

            styledDiv {
                css {
                    maxHeight = 20.em
                    overflowY = Overflow.auto
                }

                table {
                    thead {
                        tr {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    zIndex = 999
                                }
                                +"File"
                            }

                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    zIndex = 999
                                }
                                +"Progress"
                            }
                        }
                    }
                    tbody {
                        for (e in taskProgress.remainingFiles.entries) {
                            tr {
                                key = e.key

                                td {
                                    +e.key
                                }
                                td {
                                    +e.value
                                }
                            }
                        }
                    }
                }
            }
        }
    }



//    private fun RBuilder.renderColumnListing() {
//        val columnListing = props.reportState.columnListing
//
//        styledDiv {
//            css {
//                maxHeight = 10.em
//                overflowY = Overflow.auto
//                marginTop = 0.5.em
//                position = Position.relative
//            }
//
//            styledDiv {
//                css {
//                    color = Color("rgba(0, 0, 0, 0.54)")
//                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
//                    fontWeight = FontWeight.w400
//                    fontSize = 13.px
//
//                    position = Position.sticky
//                    top = 0.px
//                    left = 0.px
//                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
//                }
//                +"Columns"
//            }
//
//            when {
//                columnListing == null -> {
//                    styledDiv {
//                        +"Loading..."
//                    }
//                }
//
//                columnListing.isEmpty() -> {
//                    styledDiv {
//                        +"Not available"
//                    }
//                }
//
//                else -> {
//                    styledOl {
//                        css {
//                            marginTop = 0.px
//                            marginBottom = 0.px
////                            marginLeft = (-10).px
//                        }
//
//                        for (columnName in columnListing) {
//                            styledLi {
//                                key = columnName
//
////                                css {
////                                    display = Display.inlineBlock
////                                }
//
//                                +columnName
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

}