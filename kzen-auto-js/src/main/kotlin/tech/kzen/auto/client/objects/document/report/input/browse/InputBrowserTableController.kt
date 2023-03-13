package tech.kzen.auto.client.objects.document.report.input.browse

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.Checkbox
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.report.input.ReportInputController
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CheckIcon
import tech.kzen.auto.client.wrap.material.FolderOpenIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet
import web.html.InputType
import kotlin.math.max
import kotlin.math.min


//---------------------------------------------------------------------------------------------------------------------
external interface InputBrowserTableControllerProps: react.Props {
    var mainLocation: ObjectLocation
    var hasFilter: Boolean
    var dataLocationInfos: List<DataLocationInfo>
    var selectedDataLocation: Set<DataLocation>
    var inputBrowserState: InputBrowserState
    var inputStore: ReportInputStore
}


external interface InputBrowserTableControllerState: react.State {
    var folders: List<DataLocationInfo>
    var files: List<DataLocationInfo>
    var previousFileIndex: Int
}


//---------------------------------------------------------------------------------------------------------------------
class InputBrowserTableController(
    props: InputBrowserTableControllerProps
):
    RPureComponent<InputBrowserTableControllerProps, InputBrowserTableControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun InputBrowserTableControllerState.init(props: InputBrowserTableControllerProps) {
        val (folders, files) = props.dataLocationInfos.partition { it.directory }
        this.folders = folders
        this.files = files
        previousFileIndex = -1
    }


    override fun componentDidUpdate(
            prevProps: InputBrowserTableControllerProps,
            prevState: InputBrowserTableControllerState,
            snapshot: Any
    ) {
        if (props.dataLocationInfos != prevProps.dataLocationInfos) {
            val (folders, files) = props.dataLocationInfos.partition { it.directory }
            setState {
                this.folders = folders
                this.files = files
                previousFileIndex = -1
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun dirSelectedAsync(dir: DataLocation) {
        props.inputStore.browser.browserDirSelectedAsync(dir)
    }


    private fun onFileSelectedToggle(path: DataLocation, fileIndex: Int, shiftKey: Boolean) {
        if (shiftKey && state.previousFileIndex != -1) {
            onFileSelectedToggleRange(fileIndex)
        }
        else {
            onFileSelectedToggleSingle(path)
        }

        setState {
            previousFileIndex = fileIndex
        }
    }


    private fun onFileSelectedToggleRange(fileIndex: Int) {
        val minIndex = min(state.previousFileIndex, fileIndex)
        val maxIndex = max(state.previousFileIndex, fileIndex)
        val paths = state.files.subList(minIndex, maxIndex + 1).map { it.path }

        val selected = props.inputBrowserState.browserChecked
        val initialPath = state.files[state.previousFileIndex]
        val initialPreviousChecked = selected.contains(initialPath.path)

        val nextSelected =
            if (initialPreviousChecked) {
                selected.addAll(paths)
            }
            else {
                selected.removeAll(paths)
            }

        props.inputStore.browser.browserCheckedUpdate(nextSelected)
    }


    private fun onFileSelectedToggleSingle(path: DataLocation) {
        val selected = props.inputBrowserState.browserChecked
        val previousChecked = selected.contains(path)

        val nextSelected =
            if (previousChecked) {
                selected.remove(path)
            }
            else {
                selected.add(path)
            }

        props.inputStore.browser.browserCheckedUpdate(nextSelected)
    }


    private fun onFileSelectedAllToggle(allSelected: Boolean) {
        val nextSelected =
            if (allSelected) {
                persistentSetOf()
            }
            else {
                props
                    .inputBrowserState
                    .browserInfo!!
                    .files
                    .filter { ! it.directory }
                    .map { it.path }
                    .toPersistentSet()
            }

        props.inputStore.browser.browserCheckedUpdate(nextSelected)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.dataLocationInfos.isEmpty()) {
            renderEmpty()
        }
        else {
            renderNonEmpty()
        }
    }


    private fun ChildrenBuilder.renderEmpty() {
        div {
            val filterSuffix = when (props.hasFilter) {
                true -> " or adjust filter"
                false -> ""
            }

            +"Empty (please select different folder$filterSuffix above)"
        }
    }


    private fun ChildrenBuilder.renderNonEmpty() {
        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                borderWidth = 2.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.lightgray
            }

            table {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                renderTableHeader()
                renderTableBody()
            }
        }
    }


    private fun ChildrenBuilder.renderTableHeader() {
        val selected = props.inputBrowserState.browserChecked

        thead {
            tr {
                th {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = NamedColor.white
                        zIndex = integer(999)
                        width = 2.em
                        height = 2.em
                        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
                    }

                    var allSelected = false
                    Checkbox {
                        css {
                            marginTop = (-0.5).em
                            marginBottom = (-0.5).em
                            marginLeft = (-0.25).em
                            marginRight = (-0.25).em
                            backgroundColor = NamedColor.transparent
                            height = 0.px
                            overflow = Overflow.visible
                        }
                        disableRipple = true

                        if (state.files.isEmpty()) {
                            disabled = true
                            checked = false
                            indeterminate = false
                        }
                        else {
                            disabled = false
                            if (selected.isNotEmpty()) {
                                if (selected.size == state.files.size) {
                                    checked = true
                                    indeterminate = false
                                    allSelected = true
                                }
                                else {
                                    checked = false
                                    indeterminate = true
                                }
                            }
                            else {
                                checked = false
                                indeterminate = false
                            }
                        }
                        onChange = { _, _ ->
                            onFileSelectedAllToggle(allSelected)
                        }
                    }

                    title = when {
                        state.files.isEmpty() -> "No files"
                        allSelected -> "Un-select all"
                        else -> "Select all"
                    }
                }

                th {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = NamedColor.white
                        zIndex = integer(999)
                        width = 100.pct
                        textAlign = TextAlign.left
                        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
                    }
                    +"Name"
                }
                th {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = NamedColor.white
                        zIndex = integer(999)
                        textAlign = TextAlign.left
                        paddingLeft = 0.5.em
                        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
                    }
                    +"Selected"
                }
                th {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = NamedColor.white
                        zIndex = integer(999)
                        textAlign = TextAlign.left
                        paddingLeft = 0.5.em
                        paddingRight = 0.5.em
                        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
                    }
                    +"Modified"
                }
                th {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = NamedColor.white
                        zIndex = integer(999)
                        paddingRight = 0.5.em
                        textAlign = TextAlign.left
                        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
                    }
                    +"Size"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderTableBody() {
        tbody {
            css {
                if (props.inputBrowserState.browserInfoLoading) {
                    opacity = number(0.5)
                }
            }

            renderFolderRows()
            renderFileRows()
        }
    }


    private fun ChildrenBuilder.renderFolderRows() {
        for (folderInfo in state.folders) {
            tr {
                key = folderInfo.path.asString()

                onClick = {
                    dirSelectedAsync(folderInfo.path)
                }

                css {
                    cursor = Cursor.pointer
                    hover {
                        backgroundColor = NamedColor.lightgrey
                    }
                }

                td {
                    div {
                        css {
                            height = 1.em
                            overflow = Overflow.hidden
                        }
                        FolderOpenIcon::class.react {
                            style = jso {
                                marginTop = (-4).px
                                marginLeft = 0.15.em
                                marginRight = 0.15.em
                            }
                        }
                    }
                }

                td {
                    +folderInfo.name
                }

                td {}

                td {
                    css {
                        paddingLeft = 0.5.em
                        paddingRight = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +FormatUtils.formatLocalDateTime(folderInfo.modified)
                }

                td {}
            }
        }
    }


    private fun ChildrenBuilder.renderFileRows() {
        for ((index, fileInfo) in state.files.withIndex()) {
            val checked = fileInfo.path in props.inputBrowserState.browserChecked
            val selected = fileInfo.path in props.selectedDataLocation

            tr {
                key = fileInfo.path.asString()

                css {
                    cursor = Cursor.pointer
                    hover {
                        backgroundColor =
                            if (checked) {
                                ReportInputController.selectedHoverRow
                            }
                            else {
                                ReportInputController.hoverRow
                            }
                    }
                    if (checked) {
                        backgroundColor = ReportInputController.selectedRow
                    }
                }

                onClick = {
                    val dynamicEvent: dynamic = it
                    val shiftKey = dynamicEvent.shiftKey as Boolean
                    onFileSelectedToggle(fileInfo.path, index, shiftKey)
                }

                td {
                    input {
                        type = InputType.checkbox

                        css {
                            marginLeft = 0.5.em
                        }

                        this.checked = checked
                        this.readOnly = true
//                        this.onChange = {}

//                        // https://github.com/JetBrains/kotlin-wrappers/issues/35#issuecomment-723471655
//                        attrs["checked"] = checked
////                        attrs["disabled"] = props.editDisabled
//                        attrs["onChange"] = {}
                    }
                }
                td {
                    css {
                        if (selected) {
                            fontWeight = FontWeight.bold
                        }
                    }
                    +fileInfo.name
                }
                td {
                    css {
                        paddingLeft = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    if (selected) {
                        CheckIcon::class.react {
                            style = jso {
                                marginTop = (-0.2).em
                                marginBottom = (-0.2).em
                            }
                        }
                    }
                }
                td {
                    css {
                        paddingLeft = 0.5.em
                        paddingRight = 1.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +FormatUtils.formatLocalDateTime(fileInfo.modified)
                }
                td {
                    css {
                        textAlign = TextAlign.right
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +FormatUtils.readableFileSize(fileInfo.size)
                }
            }
        }
    }
}