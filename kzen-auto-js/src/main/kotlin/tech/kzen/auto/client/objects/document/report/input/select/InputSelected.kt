package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.input.browse.InputBrowser
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
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet


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
        var selected: PersistentSet<DataLocation>
//        var selectedOpen: Boolean
        var showFolders: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        selectedOpen = false
        selected = persistentSetOf()
        showFolders = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemoveSelected() {
        if (props.editDisabled || state.selected.isEmpty()) {
            return
        }

        if (state.selected.size == 1) {
            val path = state.selected.single()
            val inputSelectionSpecs = dataLocationToSpec(listOf(path))
            props.dispatcher.dispatchAsync(
                InputsSelectionRemoveRequest(
                    inputSelectionSpecs))
        }
        else {
            val inputSelectionSpecs = dataLocationToSpec(state.selected)
            props.dispatcher.dispatchAsync(
                InputsSelectionRemoveRequest(
                    inputSelectionSpecs))
        }

        setState {
            selected = persistentSetOf()
        }
    }


    private fun onToggleFolders() {
        setState {
            showFolders = ! showFolders
        }
    }


    private fun onFileSelectedToggle(path: DataLocation) {
        val previousChecked = state.selected.contains(path)
        setState {
            selected =
                if (previousChecked) {
                    selected.remove(path)
                }
                else {
                    selected.add(path)
                }
        }
    }


    private fun onFileSelectedAllToggle(allSelected: Boolean) {
        setState {
            selected =
                if (allSelected) {
                    persistentSetOf()
                }
                else {
                    props
                        .reportState
                        .inputSpec()
                        .selection
                        .locations
                        .map { it.location }
                        .toPersistentSet()
                }
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


    private fun dataLocationToSpec(dataLocations: Collection<DataLocation>): List<InputDataSpec> {
        val dataLocationsSet = dataLocations.toSet()
        val inputSelectionSpec = props.reportState.inputSpec().selection
        return inputSelectionSpec.locations.filter { it.location in dataLocationsSet }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectionSpec = props.reportState.inputSpec().selection.locations
        val selectionInfo = props.reportState.inputSelection

        if (selectionSpec.isEmpty()) {
            return
        }

        if (props.browserOpen) {
            styledDiv {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    marginTop = 1.em
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Selected"
            }
        }

        val reportProgress = props.reportState.reportProgress

        renderControls()

        renderTable(selectionSpec, selectionInfo, reportProgress)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderControls() {
        styledDiv {
            css {
                width = 100.pct
            }

            styledDiv {
                css {
                    marginRight = 2.em
                    display = Display.inlineBlock
                }
                renderRemove()
            }

            styledDiv {
                css {
                    marginRight = 2.em
                    display = Display.inlineBlock
                }
                renderTypeSelect()
            }

            styledDiv {
                css {
                    marginRight = 2.em
                    display = Display.inlineBlock
                }
                renderFormatSelect()
            }

            styledDiv {
                css {
                    float = Float.right
                    display = Display.inlineBlock
//                    marginTop = 1.em
                    paddingTop = 1.2.em
//                    height = 3.em
//                    backgroundColor = Color.yellow
                }
                renderDetailToggle()
            }
        }
    }


    private fun RBuilder.renderRemove() {
        val selectedRowCount = state.selected.size

        child(MaterialButton::class) {
            attrs {
                variant = "outlined"
                size = "small"

                onClick = {
                    onRemoveSelected()
                }

                if (selectedRowCount == 0) {
                    disabled = true
                    title = "No files selected"
                }
                else if (props.editDisabled) {
                    disabled = true
                    title = "Disabled while running"
                }
            }

            child(RemoveCircleOutlineIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }

            if (selectedRowCount == 0) {
                +"Remove"
            }
            else {
                +"Remove ($selectedRowCount files)"
            }
        }
    }


    private fun RBuilder.renderTypeSelect() {
        child(InputSelectedType::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                editDisabled = props.editDisabled
            }
        }
    }


    private fun RBuilder.renderFormatSelect() {
        child(InputSelectedFormat::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                editDisabled = props.editDisabled
            }
        }
    }


    private fun RBuilder.renderDetailToggle() {
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

//                    paddingLeft = 0.px
//                    paddingRight = 0.px
                }

                title =
                    if (state.showFolders) {
                        "Hide details"
                    }
                    else {
                        "Show details"
                    }
            }

            child(MoreHorizIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }

            +"Details"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderTable(
        selectionSpec: List<InputDataSpec>,
        selectedInfo: InputSelectionInfo?,
        reportProgress: ReportProgress?
    ) {
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

//                                    disabled = false
                                    if (state.selected.isNotEmpty()) {
                                        if (state.selected.size == selectionSpec.size) {
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

                                    onChange = { onFileSelectedAllToggle(allSelected) }
                                }
                            }

                            attrs {
                                title =
                                    if (allSelected) {
                                        "Un-select all"
                                    }
                                    else {
                                        "Select all"
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
                                minWidth = 9.5.em
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

                styledTbody {
                    css {
                        cursor = Cursor.default
                    }

                    val inputDataInfoByPath = selectedInfo
                        ?.locations
                        ?.groupBy { it.dataLocationInfo.path }
                        ?.mapValues { it.value.single() }
                        ?: mapOf()

                    for (inputDataSpec in selectionSpec) {
                        val inputDataInfo = inputDataInfoByPath[inputDataSpec.location]
                        renderTableRow(inputDataSpec, inputDataInfo, reportProgress)
                    }
                }
            }
        }
    }


    private fun RBuilder.renderTableRow(
        inputDataSpec: InputDataSpec,
        inputDataInfo: InputDataInfo?,
        reportProgress: ReportProgress?
    ) {
        val dataLocation = inputDataSpec.location

        val fileInfo = inputDataInfo?.dataLocationInfo
        val fileProgress = reportProgress?.inputs?.get(dataLocation)
        val checked = dataLocation in state.selected
        val missing = inputDataInfo?.dataLocationInfo?.isMissing() ?: false

        styledTr {
            key = dataLocation.asString()

            css {
                cursor = Cursor.pointer
                hover {
                    backgroundColor = InputBrowser.hoverRow
                }
            }

            attrs {
                onClickFunction = {
                    onFileSelectedToggle(dataLocation)
                }
            }

            styledTd {
                styledInput(InputType.checkBox) {
                    css {
                        marginLeft = 0.5.em
                    }

                    // https://github.com/JetBrains/kotlin-wrappers/issues/35#issuecomment-723471655
                    attrs["checked"] = checked
                    attrs["disabled"] = props.editDisabled
                    attrs["onChange"] = {}
                }
            }

            td {
                when {
                    missing ->
                        styledSpan {
                            css {
                                color = Color.gray
                            }
                            +dataLocation.fileName()
                            +" (missing)"
                        }

                    fileProgress != null ->
                        styledDiv {
                            css {
                                if (fileProgress.running) {
                                    fontWeight = FontWeight.bold
                                } else if (fileProgress.finished) {
                                    color = Color.darkGreen
                                }
                            }

                            +dataLocation.fileName()
                        }

                    else ->
                        +dataLocation.fileName()
                }

                if (state.showFolders) {
                    styledDiv {
                        css {
                            fontFamily = "monospace"
                        }
                        +dataLocation.asString()
                    }
                }

                if (fileProgress != null && fileInfo != null) {
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
                    paddingRight = 1.em
                    whiteSpace = WhiteSpace.nowrap
                }

                if (fileInfo != null && ! missing) {
                    +FormatUtils.formatLocalDateTime(fileInfo.modified)
                }
            }

            styledTd {
                css {
                    paddingRight = 0.5.em
                    textAlign = TextAlign.right
                    whiteSpace = WhiteSpace.nowrap
                }

                if (fileInfo != null && ! missing) {
                    +FormatUtils.readableFileSize(fileInfo.size)
                }
            }
        }
    }
}