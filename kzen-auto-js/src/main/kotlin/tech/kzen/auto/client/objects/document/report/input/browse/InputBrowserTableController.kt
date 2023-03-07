package tech.kzen.auto.client.objects.document.report.input.browse
//
//import kotlinx.css.*
//import kotlinx.css.properties.boxShadowInset
//import kotlinx.html.InputType
//import kotlinx.html.js.onClickFunction
//import kotlinx.html.title
//import react.RBuilder
//import react.RPureComponent
//import react.dom.attrs
//import react.dom.td
//import react.setState
//import styled.*
//import tech.kzen.auto.client.objects.document.report.input.ReportInputController
//import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
//import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
//import tech.kzen.auto.client.wrap.material.CheckIcon
//import tech.kzen.auto.client.wrap.material.FolderOpenIcon
//import tech.kzen.auto.client.wrap.material.MaterialCheckbox
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.util.FormatUtils
//import tech.kzen.auto.common.util.data.DataLocation
//import tech.kzen.auto.common.util.data.DataLocationInfo
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.platform.collect.persistentSetOf
//import tech.kzen.lib.platform.collect.toPersistentSet
//import kotlin.math.max
//import kotlin.math.min
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface InputBrowserTableControllerProps: react.Props {
//    var mainLocation: ObjectLocation
//    var hasFilter: Boolean
//    var dataLocationInfos: List<DataLocationInfo>
//    var selectedDataLocation: Set<DataLocation>
//    var inputBrowserState: InputBrowserState
//    var inputStore: ReportInputStore
//}
//
//
//external interface InputBrowserTableControllerState: react.State {
//    var folders: List<DataLocationInfo>
//    var files: List<DataLocationInfo>
//    var previousFileIndex: Int
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class InputBrowserTableController(
//    props: InputBrowserTableControllerProps
//):
//    RPureComponent<InputBrowserTableControllerProps, InputBrowserTableControllerState>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun InputBrowserTableControllerState.init(props: InputBrowserTableControllerProps) {
//        val (folders, files) = props.dataLocationInfos.partition { it.directory }
//        this.folders = folders
//        this.files = files
//        previousFileIndex = -1
//    }
//
//
//    override fun componentDidUpdate(
//            prevProps: InputBrowserTableControllerProps,
//            prevState: InputBrowserTableControllerState,
//            snapshot: Any
//    ) {
//        if (props.dataLocationInfos != prevProps.dataLocationInfos) {
//            val (folders, files) = props.dataLocationInfos.partition { it.directory }
//            setState {
//                this.folders = folders
//                this.files = files
//                previousFileIndex = -1
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun dirSelectedAsync(dir: DataLocation) {
//        props.inputStore.browser.browserDirSelectedAsync(dir)
//    }
//
//
//    private fun onFileSelectedToggle(path: DataLocation, fileIndex: Int, shiftKey: Boolean) {
//        if (shiftKey && state.previousFileIndex != -1) {
//            onFileSelectedToggleRange(fileIndex)
//        }
//        else {
//            onFileSelectedToggleSingle(path)
//        }
//
//        setState {
//            previousFileIndex = fileIndex
//        }
//    }
//
//
//    private fun onFileSelectedToggleRange(fileIndex: Int) {
//        val minIndex = min(state.previousFileIndex, fileIndex)
//        val maxIndex = max(state.previousFileIndex, fileIndex)
//        val paths = state.files.subList(minIndex, maxIndex + 1).map { it.path }
//
//        val selected = props.inputBrowserState.browserChecked
//        val initialPath = state.files[state.previousFileIndex]
//        val initialPreviousChecked = selected.contains(initialPath.path)
//
//        val nextSelected =
//            if (initialPreviousChecked) {
//                selected.addAll(paths)
//            }
//            else {
//                selected.removeAll(paths)
//            }
//
//        props.inputStore.browser.browserCheckedUpdate(nextSelected)
//    }
//
//
//    private fun onFileSelectedToggleSingle(path: DataLocation) {
//        val selected = props.inputBrowserState.browserChecked
//        val previousChecked = selected.contains(path)
//
//        val nextSelected =
//            if (previousChecked) {
//                selected.remove(path)
//            }
//            else {
//                selected.add(path)
//            }
//
//        props.inputStore.browser.browserCheckedUpdate(nextSelected)
//    }
//
//
//    private fun onFileSelectedAllToggle(allSelected: Boolean) {
//        val nextSelected =
//            if (allSelected) {
//                persistentSetOf()
//            }
//            else {
//                props
//                    .inputBrowserState
//                    .browserInfo!!
//                    .files
//                    .filter { ! it.directory }
//                    .map { it.path }
//                    .toPersistentSet()
//            }
//
//        props.inputStore.browser.browserCheckedUpdate(nextSelected)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        if (props.dataLocationInfos.isEmpty()) {
//            renderEmpty()
//        }
//        else {
//            renderNonEmpty()
//        }
//    }
//
//
//    private fun RBuilder.renderEmpty() {
//        styledDiv {
//            val filterSuffix = when (props.hasFilter) {
//                true -> " or adjust filter"
//                false -> ""
//            }
//
//            +"Empty (please select different folder$filterSuffix above)"
//        }
//    }
//
//
//    private fun RBuilder.renderNonEmpty() {
//        styledDiv {
//            css {
//                maxHeight = 20.em
//                overflowY = Overflow.auto
//                borderWidth = 2.px
//                borderStyle = BorderStyle.solid
//                borderColor = Color.lightGray
//            }
//
//            styledTable {
//                css {
//                    borderCollapse = BorderCollapse.collapse
//                    width = 100.pct
//                }
//
//                renderTableHeader()
//                renderTableBody()
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderTableHeader() {
//        val selected = props.inputBrowserState.browserChecked
//
//        styledThead {
//            styledTr {
//                styledTh {
//                    css {
//                        position = Position.sticky
//                        top = 0.px
//                        backgroundColor = Color.white
//                        zIndex = 999
//                        width = 2.em
//                        height = 2.em
//                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                    }
//
//                    var allSelected = false
//                    child(MaterialCheckbox::class) {
//                        attrs {
//                            style = reactStyle {
//                                marginTop = (-0.5).em
//                                marginBottom = (-0.5).em
//                                marginLeft = (-0.25).em
//                                marginRight = (-0.25).em
//                                backgroundColor = Color.transparent
//                                height = 0.px
//                                overflow = Overflow.visible
//                            }
//                            disableRipple = true
//
//                            if (state.files.isEmpty()) {
//                                disabled = true
//                                checked = false
//                                indeterminate = false
//                            }
//                            else {
//                                disabled = false
//                                if (selected.isNotEmpty()) {
//                                    if (selected.size == state.files.size) {
//                                        checked = true
//                                        indeterminate = false
//                                        allSelected = true
//                                    }
//                                    else {
//                                        checked = false
//                                        indeterminate = true
//                                    }
//                                }
//                                else {
//                                    checked = false
//                                    indeterminate = false
//                                }
//                            }
//                            onChange = { onFileSelectedAllToggle(allSelected) }
//                        }
//                    }
//
//                    attrs {
//                        title = when {
//                            state.files.isEmpty() -> "No files"
//                            allSelected -> "Un-select all"
//                            else -> "Select all"
//                        }
//                    }
//                }
//
//                styledTh {
//                    css {
//                        position = Position.sticky
//                        top = 0.px
//                        backgroundColor = Color.white
//                        zIndex = 999
//                        width = 100.pct
//                        textAlign = TextAlign.left
//                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                    }
//                    +"Name"
//                }
//                styledTh {
//                    css {
//                        position = Position.sticky
//                        top = 0.px
//                        backgroundColor = Color.white
//                        zIndex = 999
//                        textAlign = TextAlign.left
//                        paddingLeft = 0.5.em
//                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                    }
//                    +"Selected"
//                }
//                styledTh {
//                    css {
//                        position = Position.sticky
//                        top = 0.px
//                        backgroundColor = Color.white
//                        zIndex = 999
//                        textAlign = TextAlign.left
//                        paddingLeft = 0.5.em
//                        paddingRight = 0.5.em
//                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                    }
//                    +"Modified"
//                }
//                styledTh {
//                    css {
//                        position = Position.sticky
//                        top = 0.px
//                        backgroundColor = Color.white
//                        zIndex = 999
//                        paddingRight = 0.5.em
//                        textAlign = TextAlign.left
//                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                    }
//                    +"Size"
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderTableBody() {
//        styledTbody {
//            css {
//                if (props.inputBrowserState.browserInfoLoading) {
//                    opacity = 0.5
//                }
//            }
//
//            renderFolderRows()
//            renderFileRows()
//        }
//    }
//
//
//    private fun RBuilder.renderFolderRows() {
//        for (folderInfo in state.folders) {
//            styledTr {
//                key = folderInfo.path.asString()
//
//                attrs {
//                    onClickFunction = {
//                        dirSelectedAsync(folderInfo.path)
//                    }
//                }
//
//                css {
//                    cursor = Cursor.pointer
//                    hover {
//                        backgroundColor = Color.lightGrey
//                    }
//                }
//
//                styledTd {
//                    styledDiv {
//                        css {
//                            height = 1.em
//                            overflow = Overflow.hidden
//                        }
//                        child(FolderOpenIcon::class) {
//                            attrs {
//                                style = reactStyle {
//                                    marginTop = (-4).px
//                                    marginLeft = 0.15.em
//                                    marginRight = 0.15.em
//                                }
//                            }
//                        }
//                    }
//                }
//
//                td {
//                    +folderInfo.name
//                }
//
//                styledTd {}
//
//                styledTd {
//                    css {
//                        paddingLeft = 0.5.em
//                        paddingRight = 0.5.em
//                        whiteSpace = WhiteSpace.nowrap
//                    }
//                    +FormatUtils.formatLocalDateTime(folderInfo.modified)
//                }
//
//                styledTd {}
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderFileRows() {
//        for ((index, fileInfo) in state.files.withIndex()) {
//            val checked = fileInfo.path in props.inputBrowserState.browserChecked
//            val selected = fileInfo.path in props.selectedDataLocation
//
//            styledTr {
//                key = fileInfo.path.asString()
//
//                css {
//                    cursor = Cursor.pointer
//                    hover {
//                        backgroundColor =
//                            if (checked) {
//                                ReportInputController.selectedHoverRow
//                            }
//                            else {
//                                ReportInputController.hoverRow
//                            }
//                    }
//                    if (checked) {
//                        backgroundColor = ReportInputController.selectedRow
//                    }
//                }
//
//                attrs {
//                    onClickFunction = {
//                        val dynamicEvent: dynamic = it
//                        val shiftKey = dynamicEvent.shiftKey as Boolean
//                        onFileSelectedToggle(fileInfo.path, index, shiftKey)
//                    }
//                }
//
//                td {
//                    styledInput(InputType.checkBox) {
//                        css {
//                            marginLeft = 0.5.em
//                        }
//
//                        // https://github.com/JetBrains/kotlin-wrappers/issues/35#issuecomment-723471655
//                        attrs["checked"] = checked
////                        attrs["disabled"] = props.editDisabled
//                        attrs["onChange"] = {}
//                    }
//                }
//                styledTd {
//                    css {
//                        if (selected) {
//                            fontWeight = FontWeight.bold
//                        }
//                    }
//                    +fileInfo.name
//                }
//                styledTd {
//                    css {
//                        paddingLeft = 0.5.em
//                        whiteSpace = WhiteSpace.nowrap
//                    }
//                    if (selected) {
//                        child(CheckIcon::class) {
//                            attrs {
//                                style = reactStyle {
//                                    marginTop = (-0.2).em
//                                    marginBottom = (-0.2).em
//                                }
//                            }
//                        }
//                    }
//                }
//                styledTd {
//                    css {
//                        paddingLeft = 0.5.em
//                        paddingRight = 1.em
//                        whiteSpace = WhiteSpace.nowrap
//                    }
//                    +FormatUtils.formatLocalDateTime(fileInfo.modified)
//                }
//                styledTd {
//                    css {
//                        textAlign = TextAlign.right
//                        whiteSpace = WhiteSpace.nowrap
//                    }
//                    +FormatUtils.readableFileSize(fileInfo.size)
//                }
//            }
//        }
//    }
//}