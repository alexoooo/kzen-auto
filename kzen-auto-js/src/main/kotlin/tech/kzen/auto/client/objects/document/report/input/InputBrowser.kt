package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
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
//        console.log("^^^^ State.init")
        browserOpen = false
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (props.reportState.isInitiating()) {
//            console.log("^^^ INIT")
            return
        }

        if (props.reportState.inputSelected != null &&
                props.reportState.inputSelected!!.isEmpty() &&
                ! state.browserOpen
        ) {
//            console.log("^^^^ setting browserOpen")
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
//        console.log("&&& onBrowseRefresh")
        props.dispatcher.dispatchAsync(ListInputsBrowserRequest)
    }


    private fun onToggleBrowser() {
//        console.log("&&& onToggleBrowser")
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
        val inputError = props.reportState.inputError

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

//            +"state.browserOpen - ${state.browserOpen}"

            if (state.browserOpen) {
                if (listingSelected == null && inputError != null) {
                    +"Error"
                }
                else if (listingSelected != null && inputError != null) {
                    renderPathEditError(props.reportState.inputSpec().directory)
                }
                else if (browserListing == null || browserDir == null) {
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
            css {
                marginTop = 0.5.em
                marginBottom = 0.5.em
            }
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
                                backgroundColor = Color("rgba(255, 255, 255, 0.95)")
                                zIndex = 999
                                width = 2.em
                            }

                            attrs {
                                title = "Select / un-select all"
                            }

                            child(MaterialCheckbox::class) {
                                attrs {
                                    style = reactStyle {
                                        marginTop = (-0.5).em
                                        marginBottom = (-0.25).em
                                        marginLeft = (-0.25).em
                                        marginRight = (-0.25).em
                                    }
//                                    this.checked = checked
//                                    disabled = editDisabled

//                                    onChange = {
//                                        onCriteriaChange(e.key, ! checked)
//                                    }
                                }
                            }
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                                width = 100.pct
                                textAlign = TextAlign.left
//                                verticalAlign = VerticalAlign.middle
//                                fontSize = 1.25.em
                                paddingBottom = 0.25.em
                            }
                            +"Name"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                                textAlign = TextAlign.left
                                paddingBottom = 0.25.em
                                paddingLeft = 0.5.em
                            }
                            +"Modified"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                                textAlign = TextAlign.left
                                paddingBottom = 0.25.em
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
                                            marginLeft = 0.15.em
                                            marginRight = 0.15.em
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
                                child(MaterialCheckbox::class) {
                                    attrs {
                                        style = reactStyle {
//                                            marginTop = (-0.25).em
//                                            marginBottom = (-0.25).em
                                            marginTop = (-0.5).em
                                            marginBottom = (-0.5).em
                                            marginLeft = (-0.25).em
                                            marginRight = (-0.25).em
                                        }
//                                    this.checked = checked
//                                    disabled = editDisabled

//                                    onChange = {
//                                        onCriteriaChange(e.key, ! checked)
//                                    }
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


    private fun RBuilder.renderPathEditError(browseDir: String) {
        child(InputBrowserDir::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                editDisabled = props.editDisabled
                this.browseDir = browseDir
                errorMode = true
            }
        }
    }


    private fun RBuilder.renderPathEdit(browseDir: String) {
        child(InputBrowserDir::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                editDisabled = props.editDisabled
                this.browseDir = browseDir
                errorMode = false
            }
        }
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