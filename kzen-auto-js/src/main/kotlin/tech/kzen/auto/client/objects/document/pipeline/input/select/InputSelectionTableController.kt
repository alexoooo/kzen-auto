package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.InputType
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.attrs
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.input.browse.InputBrowser
import tech.kzen.auto.client.wrap.material.MaterialCheckbox
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.InputDataInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputDataSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf


class InputSelectionTableController(
    props: Props
):
    RPureComponent<InputSelectionTableController.Props, InputSelectionTableController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var spec: InputSelectionSpec
        var selectionInfo: InputSelectionInfo?
//        var reportState: ReportState
//        var dispatcher: ReportDispatcher
//        var editDisabled: Boolean
//        var browserOpen: Boolean
    }


    interface State: RState {
        var selected: PersistentSet<DataLocation>
        var showDetails: Boolean
//        var showGroupBy: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        selected = persistentSetOf()
        showDetails = false
//        showGroupBy = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
//                borderWidth = 1.px
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

                            if (state.selected.isNotEmpty()) {
//                                if (state.selected.size == selectionSpec.size) {
//                                    checked = true
//                                    indeterminate = false
//                                    allSelected = true
//                                }
//                                else {
//                                    checked = false
//                                    indeterminate = true
//                                }
                            }
                            else {
                                checked = false
                                indeterminate = false
                            }

//                            onChange = { onFileSelectedAllToggle(allSelected) }
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

//                if (hasGroup) {
//                    styledTh {
//                        css {
//                            position = Position.sticky
//                            top = 0.px
//                            backgroundColor = Color.white
//                            zIndex = 999
//                            textAlign = TextAlign.left
//                            paddingLeft = 0.5.em
//                            paddingRight = 0.5.em
//                            boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                        }
//                        +"Group"
//                    }
//                }

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
                if (state.showDetails) {
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

            val selectionInput = props.selectionInfo

            if (selectionInput == null) {
                for (inputDataSpec in props.spec.locations) {
                    renderTableRow(inputDataSpec, null, /*reportProgress*/)
                }
            }
            else {
                val inputDataSpecByPath = props
                    .spec
                    .locations
                    .groupBy { it.location }
                    .mapValues { it.value.single() }

                for (inputDataInfo in selectionInput.locations) {
                    val inputDataSpec = inputDataSpecByPath[inputDataInfo.dataLocationInfo.path]
                        ?: continue
                    renderTableRow(inputDataSpec, inputDataInfo, /*reportProgress*/)
                }
            }
        }
    }


    private fun RBuilder.renderTableRow(
        inputDataSpec: InputDataSpec,
        inputDataInfo: InputDataInfo?,
//        reportProgress: ReportProgress?
    ) {
        val dataLocation = inputDataSpec.location

        val fileInfo = inputDataInfo?.dataLocationInfo
//        val fileProgress = reportProgress?.inputs?.get(dataLocation)
        val checked = dataLocation in state.selected
        val missing = inputDataInfo?.dataLocationInfo?.isMissing() ?: false
        val processorInvalid = inputDataInfo?.invalidProcessor ?: false
        val group = inputDataInfo?.group

        styledTr {
            key = dataLocation.asString()

            css {
                cursor = Cursor.pointer
                hover {
                    backgroundColor = InputBrowser.hoverRow
                }
            }

            attrs {
//                onClickFunction = {
//                    onFileSelectedToggle(dataLocation)
//                }
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

//                    fileProgress != null ->
//                        styledDiv {
//                            css {
//                                if (fileProgress.running) {
//                                    fontWeight = FontWeight.bold
//                                }
//                                else if (fileProgress.finished) {
//                                    color = Color.darkGreen
//                                }
//                            }
//
//                            +dataLocation.fileName()
//                        }

                    else ->
                        +dataLocation.fileName()
                }

                if (state.showDetails) {
                    styledDiv {
                        css {
                            fontFamily = "monospace"
                        }
                        +dataLocation.asString()
                    }
                }

//                if (fileProgress != null && fileInfo != null) {
//                    styledDiv {
//                        css {
//                            fontStyle = FontStyle.italic
//                            marginBottom = 0.25.em
//                        }
//                        +fileProgress.toMessage(fileInfo.size)
//                    }
//                }
            }

            if (state.showDetails) {
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