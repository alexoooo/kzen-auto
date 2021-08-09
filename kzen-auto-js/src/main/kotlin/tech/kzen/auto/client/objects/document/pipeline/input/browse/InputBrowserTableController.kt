package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.attrs
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.pipeline.input.PipelineInputController
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.wrap.material.CheckIcon
import tech.kzen.auto.client.wrap.material.FolderOpenIcon
import tech.kzen.auto.client.wrap.material.MaterialCheckbox
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet


class InputBrowserTableController(
    props: Props
):
    RPureComponent<InputBrowserTableController.Props, InputBrowserTableController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var hasFilter: Boolean
        var dataLocationInfos: List<DataLocationInfo>
        var selectedDataLocation: Set<DataLocation>
        var loading: Boolean
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var textEdit: Boolean
//        var editDir: String
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun dirSelectedAsync(dir: DataLocation) {
        props.inputStore.browser.browserDirSelectedAsync(dir)
    }


    private fun onFileSelectedToggle(path: DataLocation) {
        val selected = props.inputState.browser.browserChecked
        val previousChecked = selected.contains(path)
        val nextSelected =
            if (previousChecked) {
                selected.remove(path)
            }
            else {
                selected.add(path)
            }

        props.inputStore.browser.browserSelectionUpdate(nextSelected)
    }


    private fun onFileSelectedAllToggle(allSelected: Boolean) {
        val nextSelected =
            if (allSelected) {
                persistentSetOf()
            }
            else {
                props
                    .inputState
                    .browser
                    .browserInfo!!
                    .files
                    .filter { ! it.directory }
                    .map { it.path }
                    .toPersistentSet()
            }

        props.inputStore.browser.browserSelectionUpdate(nextSelected)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (props.dataLocationInfos.isEmpty()) {
            renderEmpty()
        }
        else {
            renderNonEmpty()
        }
    }


    private fun RBuilder.renderEmpty() {
        styledDiv {
            val filterSuffix = when (props.hasFilter) {
                true -> " or adjust filter"
                false -> ""
            }

            +"Empty (please select different folder$filterSuffix above)"
        }
    }


    private fun RBuilder.renderNonEmpty() {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                val (folders, files) = props.dataLocationInfos.partition { it.directory }
                renderTableHeader(files)
                renderTableBody(folders, files)
            }
        }
    }


    private fun RBuilder.renderTableHeader(files: List<DataLocationInfo>) {
        val selected = props.inputState.browser.browserChecked

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

                    var allSelected = false
                    child(MaterialCheckbox::class) {
                        attrs {
                            style = reactStyle {
                                marginTop = (-0.5).em
                                marginBottom = (-0.5).em
                                marginLeft = (-0.25).em
                                marginRight = (-0.25).em
                                backgroundColor = Color.transparent
                                height = 0.px
                                overflow = Overflow.visible
                            }
                            disableRipple = true

                            if (files.isEmpty()) {
                                disabled = true
                                checked = false
                                indeterminate = false
                            }
                            else {
                                disabled = false
                                if (selected.isNotEmpty()) {
                                    if (selected.size == files.size) {
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
                            onChange = { onFileSelectedAllToggle(allSelected) }
                        }
                    }

                    attrs {
                        title = when {
                            files.isEmpty() ->"No files"
                            allSelected -> "Un-select all"
                            else -> "Select all"
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
                    +"Name"
                }
                styledTh {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = Color.white
                        zIndex = 999
                        textAlign = TextAlign.left
                        paddingLeft = 0.5.em
                        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                    }
                    +"Selected"
                }
                styledTh {
                    css {
                        position = Position.sticky
                        top = 0.px
                        backgroundColor = Color.white
                        zIndex = 999
                        textAlign = TextAlign.left
                        paddingLeft = 0.5.em
                        paddingRight = 0.5.em
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
            }
        }
    }


    private fun RBuilder.renderTableBody(folders: List<DataLocationInfo>, files: List<DataLocationInfo>) {
        styledTbody {
            css {
                if (props.loading) {
                    opacity = 0.5
                }
            }

            renderFolderRows(folders)
            renderFileRows(files)
        }
    }


    private fun RBuilder.renderFolderRows(folders: List<DataLocationInfo>) {
        for (folderInfo in folders) {
            styledTr {
                key = folderInfo.path.asString()

                attrs {
                    onClickFunction = {
                        dirSelectedAsync(folderInfo.path)
                    }
                }

                css {
                    cursor = Cursor.pointer
                    hover {
                        backgroundColor = Color.lightGrey
                    }
                }

                styledTd {
                    styledDiv {
                        css {
                            height = 1.em
                            overflow = Overflow.hidden
                        }
                        child(FolderOpenIcon::class) {
                            attrs {
                                style = reactStyle {
                                    marginTop = (-4).px
                                    marginLeft = 0.15.em
                                    marginRight = 0.15.em
                                }
                            }
                        }
                    }
                }

                td {
                    +folderInfo.name
                }

                styledTd {}

                styledTd {
                    css {
                        paddingLeft = 0.5.em
                        paddingRight = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +FormatUtils.formatLocalDateTime(folderInfo.modified)
                }

                styledTd {}
            }
        }
    }


    private fun RBuilder.renderFileRows(files: List<DataLocationInfo>) {
        for (fileInfo in files) {
            val checked = fileInfo.path in props.inputState.browser.browserChecked
            val selected = fileInfo.path in props.selectedDataLocation

            styledTr {
                key = fileInfo.path.asString()

                css {
                    cursor = Cursor.pointer
                    hover {
                        backgroundColor =
                            if (checked) {
                                PipelineInputController.selectedHoverRow
                            }
                            else {
                                PipelineInputController.hoverRow
                            }
                    }
                    if (checked) {
                        backgroundColor = PipelineInputController.selectedRow
                    }
                }

                attrs {
                    onClickFunction = {
                        onFileSelectedToggle(fileInfo.path)
                    }
                }

                td {
                    styledInput(InputType.checkBox) {
                        css {
                            marginLeft = 0.5.em
                        }

                        // https://github.com/JetBrains/kotlin-wrappers/issues/35#issuecomment-723471655
                        attrs["checked"] = checked
//                        attrs["disabled"] = props.editDisabled
                        attrs["onChange"] = {}
                    }
                }
                styledTd {
                    css {
                        if (selected) {
                            fontWeight = FontWeight.bold
                        }
                    }
                    +fileInfo.name
                }
                styledTd {
                    css {
                        paddingLeft = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    if (selected) {
                        child(CheckIcon::class) {
                            attrs {
                                style = reactStyle {
                                    marginTop = (-0.2).em
                                    marginBottom = (-0.2).em
                                }
                            }
                        }
                    }
                }
                styledTd {
                    css {
                        paddingLeft = 0.5.em
                        paddingRight = 1.em
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +FormatUtils.formatLocalDateTime(fileInfo.modified)
                }
                styledTd {
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