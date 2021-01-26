package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.td
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.report.state.ListInputsBrowserNavigate
import tech.kzen.auto.client.objects.document.report.state.ListInputsBrowserRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.listing.FileInfo
import tech.kzen.auto.common.util.FormatUtils


class InputBrowser(
    props: Props
):
    RPureComponent<InputBrowser.Props, InputBrowser.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private fun formatModified(time: Instant): String {
            val modifiedLocal = time.toLocalDateTime(TimeZone.currentSystemDefault())
            val hours = modifiedLocal.hour.toString().padStart(2, '0')
            val minutes = modifiedLocal.minute.toString().padStart(2, '0')
            val seconds = modifiedLocal.second.toString().padStart(2, '0')
            return "${modifiedLocal.date} $hours:$minutes:$seconds"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
        var browserOpen: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        browserOpen = false
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (props.reportState.isInitiating()) {
            return
        }

        if (props.reportState.inputSelected != null &&
                props.reportState.inputSelected!!.isEmpty() &&
                ! state.browserOpen
        ) {
            setState {
                browserOpen = true
            }
        }

        if (state.browserOpen && ! prevState.browserOpen &&
                props.reportState.inputBrowser == null
        ) {
            onBrowseRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onBrowseRefresh() {
        props.dispatcher.dispatchAsync(ListInputsBrowserRequest)
    }


    private fun onToggleBrowser() {
        setState {
            browserOpen = ! browserOpen
        }
    }


    private fun onDirSelected(dir: String) {
        props.dispatcher.dispatchAsync(ListInputsBrowserNavigate(dir))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val listingSelected = props.reportState.inputSelected
        val browserListing = props.reportState.inputBrowser
        val browserDir = props.reportState.inputBrowseDir

        val forceOpen =
            listingSelected != null && listingSelected.isEmpty()

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
            }

            styledDiv {
                css {
                    width = 100.pct
                }

                styledSpan {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Browser"
                }

                if (! forceOpen) {
                    styledSpan {
                        css {
                            float = Float.right
                        }

                        child(MaterialIconButton::class) {
                            attrs {
                                onClick = {
                                    onToggleBrowser()
                                }
                            }

                            if (state.browserOpen) {
                                child(ExpandLessIcon::class) {}
                            } else {
                                child(ExpandMoreIcon::class) {}
                            }
                        }
                    }
                }
            }

            if (state.browserOpen) {
                if (browserListing == null || browserDir == null) {
                    styledDiv {
                        if (browserDir != null) {
                            +browserDir
                        }
                        else {
                            +props.reportState.inputSpec().directory
                        }
                    }
                    +"Loading..."
                }
                else {
                    renderDetail(browserListing, browserDir)
                }
            }
            else {
                renderSummary(props.reportState.inputBrowseDir)
            }

//            child(DefaultAttributeEditor::class) {
//                attrs {
//                    clientState = props.reportState.clientState
//                    objectLocation = props.reportState.mainLocation
//                    attributeName = ReportConventions.inputAttributeName
//                    labelOverride = "File Path"
//
//                    disabled = editDisabled
//                    invalid = hasError
//
//                    onChange = {
//                        onAttributeChanged()
//                    }
//                }
//            }
        }
    }


    private fun RBuilder.renderDetail(browserListing: List<FileInfo>, browserDir: String) {
        styledDiv {
            +"[+ Add]"
            +"[- Remove]"

            +"[Min]"
            +"[Max]"
            +"[Filter]"
        }

        styledDiv {
            renderPathEdit(browserDir)
        }

        val (folders, files) = browserListing.partition { it.directory }

        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                thead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"[x]"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                                width = 100.pct
                            }
                            +"Name"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Modified"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Size"
                        }
                    }
                }
                styledTbody {
                    for (folderInfo in folders) {
                        styledTr {
                            key = folderInfo.path

                            attrs {
                                onClickFunction = {
                                    onDirSelected(folderInfo.path)
                                }
                            }

                            css {
                                cursor = Cursor.pointer
                                hover {
                                    backgroundColor = Color.lightGrey
                                }
                            }

                            styledTd {
                                child(FolderOpenIcon::class) {
                                    attrs {
                                        style = reactStyle {
                                            marginTop = (-0.25).em
                                            marginBottom = (-0.25).em
                                        }
                                    }
                                }
                            }
                            td {
                                +folderInfo.name
                            }
                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    whiteSpace = WhiteSpace.nowrap
                                }
                                +formatModified(folderInfo.modified)
                            }
                            styledTd {}
                        }
                    }

                    for (fileInfo in files) {
                        styledTr {
                            key = fileInfo.path

                            css {
                                cursor = Cursor.pointer
                                hover {
                                    backgroundColor = Color.lightGrey
                                }
                            }

                            td {
                                +"[ ]"
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
                                +formatModified(fileInfo.modified)
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
        }
    }


    private fun RBuilder.renderPathEdit(browseDir: String) {
        +"[Path: $browseDir]"
    }


    private fun RBuilder.renderSummary(browseDir: String?) {
        if (browseDir != null) {
            +browseDir
        }
        else {
            +props.reportState.inputSpec().directory
        }
    }
}