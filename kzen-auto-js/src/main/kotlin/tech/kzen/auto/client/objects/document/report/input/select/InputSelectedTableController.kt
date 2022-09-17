package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.attrs
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.input.ReportInputController
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
import tech.kzen.auto.client.wrap.material.MaterialCheckbox
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.InputDataInfo
import tech.kzen.auto.common.objects.document.report.progress.ReportFileProgress
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet
import kotlin.math.max
import kotlin.math.min


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedTableControllerProps: Props {
    var showDetails: Boolean
    var spec: InputSelectionSpec
    var inputSelectedState: InputSelectedState
    var progress: ReportRunProgress?
    var inputStore: ReportInputStore
}


external interface InputSelectedTableControllerState: State {
    var selected: List<Pair<InputDataSpec, InputDataInfo?>>
    var previousSelectedIndex: Int
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedTableController(
    props: InputSelectedTableControllerProps
):
    RPureComponent<InputSelectedTableControllerProps, InputSelectedTableControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun InputSelectedTableControllerState.init(props: InputSelectedTableControllerProps) {
        selected = extractSelected(props)
        previousSelectedIndex = -1
    }


    override fun componentDidUpdate(
            prevProps: InputSelectedTableControllerProps,
            prevState: InputSelectedTableControllerState,
            snapshot: Any
    ) {
        if (props.inputSelectedState.selectedInfo != prevProps.inputSelectedState.selectedInfo ||
                props.spec.locations != prevProps.spec.locations
        ) {
            setState {
                selected = extractSelected(props)
                previousSelectedIndex = -1
            }
        }
    }


    private fun extractSelected(props: InputSelectedTableControllerProps): List<Pair<InputDataSpec, InputDataInfo?>> {
        val selectedInput = props.inputSelectedState.selectedInfo
            ?: return props.spec.locations.map { it to null }

        val inputDataSpecByPath = props
            .spec
            .locations
            .groupBy { it.location }
            .mapValues { it.value.single() }

        return selectedInput
            .locations
            .filter { it.dataLocationInfo.path in inputDataSpecByPath }
            .map { inputDataSpecByPath[it.dataLocationInfo.path]!! to it }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onFileSelectedToggle(dataLocation: DataLocation, index: Int, shiftKey: Boolean) {
        if (shiftKey && state.previousSelectedIndex != -1) {
            onFileSelectedToggleRange(index)
        }
        else {
            onFileSelectedToggleSingle(dataLocation)
        }

        setState {
            previousSelectedIndex = index
        }
    }


    private fun onFileSelectedToggleRange(index: Int) {
        val minIndex = min(state.previousSelectedIndex, index)
        val maxIndex = max(state.previousSelectedIndex, index)
        val paths = state.selected.subList(minIndex, maxIndex + 1).map { it.first.location }

        val selected = props.inputSelectedState.selectedChecked
        val initialSelected = state.selected[state.previousSelectedIndex]
        val initialPreviousChecked = selected.contains(initialSelected.first.location)

        val nextSelected =
            if (initialPreviousChecked) {
                selected.addAll(paths)
            }
            else {
                selected.removeAll(paths)
            }

        props.inputStore.selected.checkedUpdate(nextSelected)
    }


    private fun onFileSelectedToggleSingle(dataLocation: DataLocation) {
        val selectedChecked = props.inputSelectedState.selectedChecked
        val previousChecked = selectedChecked.contains(dataLocation)

        val nextCheckedDataLocations =
            if (previousChecked) {
                selectedChecked.remove(dataLocation)
            }
            else {
                selectedChecked.add(dataLocation)
            }

        props.inputStore.selected.checkedUpdate(nextCheckedDataLocations)
    }


    private fun onFileSelectedAllToggle(allSelected: Boolean) {
        val nextCheckedDataLocations =
            if (allSelected) {
                persistentSetOf()
            }
            else {
                props
                    .spec
                    .locations
                    .map { it.location }
                    .toPersistentSet()
            }

        props.inputStore.selected.checkedUpdate(nextCheckedDataLocations)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
                marginTop = 0.5.em
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                renderHeader()
                renderBody()
            }
        }
    }


    private fun RBuilder.renderHeader() {
        val hasGroup = props.spec.groupBy.isNotBlank()

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

                            if (props.inputSelectedState.selectedChecked.isNotEmpty()) {
                                if (props.inputSelectedState.selectedChecked.size ==
                                        props.spec.locations.size
                                ) {
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

                if (hasGroup) {
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
                        +"Group"
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
                if (props.showDetails) {
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
                        +"Format"
                    }
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
    }


    private fun RBuilder.renderBody() {
        styledTbody {
            css {
                cursor = Cursor.default
            }

            val selected = state.selected
            for ((index, e) in selected.withIndex()) {
                renderTableRow(e.first, e.second, index)
            }
        }
    }


    private fun RBuilder.renderTableRow(
        inputDataSpec: InputDataSpec,
        inputDataInfo: InputDataInfo?,
        index: Int
    ) {
        val dataLocation = inputDataSpec.location

        val fileInfo = inputDataInfo?.dataLocationInfo

        val fileProgress = props
            .progress
            ?.snapshot
            ?.values
            ?.get(ReportConventions.inputTracePath(dataLocation))
            ?.get()
            ?.let {
                @Suppress("UNCHECKED_CAST")
                ReportFileProgress.fromCollection(it as Map<String, Any>)
            }

        val checked = dataLocation in props.inputSelectedState.selectedChecked
        val missing = inputDataInfo?.dataLocationInfo?.isMissing() ?: false
        val processorInvalid = inputDataInfo?.invalidProcessor ?: false
        val group = inputDataInfo?.group

        styledTr {
            key = dataLocation.asString()

            css {
                cursor = Cursor.pointer
                hover {
                    backgroundColor = ReportInputController.hoverRow
                }
            }

            attrs {
                onClickFunction = {
                    val dynamicEvent: dynamic = it
                    val shiftKey = dynamicEvent.shiftKey as Boolean
                    onFileSelectedToggle(dataLocation, index, shiftKey)
                }
            }

            styledTd {
                styledInput(InputType.checkBox) {
                    css {
                        marginLeft = 0.5.em
                    }

                    // https://github.com/JetBrains/kotlin-wrappers/issues/35#issuecomment-723471655
                    attrs["checked"] = checked
//                    attrs["disabled"] = props.editDisabled
                    attrs["onChange"] = {}
                }
            }

            if (group?.group != null) {
                styledTd {
                    css {
                        paddingLeft = 0.5.em
                        paddingRight = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }

                    +group.group!!
                }
            }

            td {
                when {
                    missing || processorInvalid ->
                        styledSpan {
                            css {
                                color = Color.gray
                            }
                            +dataLocation.fileName()

                            val reason = when {
                                missing -> "missing"
                                else -> "invalid format"
                            }

                            +" ($reason)"
                        }

                    fileProgress != null ->
                        styledDiv {
                            css {
                                if (fileProgress.running) {
                                    fontWeight = FontWeight.bold
                                }
                                else if (fileProgress.finished) {
                                    color = Color.darkGreen
                                }
                                else {
                                    color = Color.brown
                                }
                            }

                            +dataLocation.fileName()
                        }

                    else ->
                        +dataLocation.fileName()
                }

                if (props.showDetails) {
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

            if (props.showDetails) {
                styledTd {
                    css {
                        paddingLeft = 0.5.em
                        whiteSpace = WhiteSpace.nowrap
                    }

                    +inputDataSpec.processorDefinitionCoordinate.asString()
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