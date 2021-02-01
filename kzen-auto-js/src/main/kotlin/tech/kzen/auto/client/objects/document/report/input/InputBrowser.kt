package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.state.ListInputsBrowserNavigate
import tech.kzen.auto.client.objects.document.report.state.ListInputsBrowserRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.listing.FileInfo
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet


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

        private val hoverRow = Color("rgb(200, 200, 200)")
        private val selectedRow = Color("rgb(200, 200, 255)")
        private val selectedHoverRow = Color("rgb(175, 175, 227)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
        var browserOpen: Boolean
        var selected: PersistentSet<String>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("^^^^ State.init")
        browserOpen = false
        selected = persistentSetOf()
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


    private fun onFileSelectedToggle(path: String) {
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
                        .inputBrowser!!
                        .filter { ! it.directory }
                        .map { it.path }
                        .toPersistentSet()
                }
        }
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

            styledDiv {
                css {
                    marginTop = 0.5.em
                }

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
    }


    private fun RBuilder.renderDetail(browserListing: List<FileInfo>, browserDir: String) {
        renderControls()

        styledDiv {
            css {
                marginTop = 0.5.em
                marginBottom = 0.5.em
            }
            renderPathEdit(browserDir)
        }

        renderFileTable(browserListing)
    }


    private fun RBuilder.renderControls(/*browserListing: List<FileInfo>*/) {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    style = reactStyle {
                        marginRight = 1.em
                    }

                    onClick = {
//                        onSummaryRefresh()
                    }

                    if (state.selected.isEmpty()) {
                        disabled = true
                        title = "No files selected"
                    }
                }

                child(AddIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }

                if (state.selected.isEmpty()) {
                    +"Add"
                }
                else {
                    +"Add (${state.selected.size} files)"
                }
            }

            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
//                        onSummaryRefresh()
                    }

                    if (state.selected.isEmpty()) {
                        disabled = true
                        title = "No files selected"
                    }
                }

                child(RemoveIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }

                if (state.selected.isEmpty()) {
                    +"Remove"
                }
                else {
                    +"Remove (${state.selected.size} files)"
                }
            }

            child(MaterialTextField::class) {
                attrs {
                    style = reactStyle {
                        float = Float.right
                    }

                    size = "small"

                    InputProps = object : RProps {
                        @Suppress("unused")
                        var startAdornment = child(MaterialInputAdornment::class) {
                            attrs {
                                position = "start"
                            }
                            child(SearchIcon::class) {}
                        }
                    }

                    onChange = {
                        val target = it.target as HTMLInputElement
//                        onValueChange(target.value)
                    }

                    disabled = props.editDisabled
                }
            }
        }
    }


    private fun RBuilder.renderFileTable(browserListing: List<FileInfo>) {
        if (browserListing.isEmpty()) {
            styledDiv {
                css {
//                    fontSize = 1.25.em
//                    fontWeight = FontWeight.bold
//                    marginLeft = 0.5.em
                }
                +"Empty (please select different folder above)"
            }
            return
        }

        val (folders, files) = browserListing.partition { it.directory }

        // see: https://stackoverflow.com/questions/1122381/how-to-force-child-div-to-be-100-of-parent-divs-height-without-specifying-pare
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

                            var allSelected = false
//                            styledDiv {
//                                css {
//                                    position = Position.relative
//                                }
                                child(MaterialCheckbox::class) {
                                    attrs {
                                        style = reactStyle {
//                                            position = Position.absolute
//                                            top = (-0.5).em
//                                            left = (-0.25).em
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
                                            disabled = props.editDisabled
                                            if (state.selected.isNotEmpty()) {
                                                if (state.selected.size == files.size) {
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
//                            }

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
                                height = 2.em
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
                        val checked = state.selected.contains(fileInfo.path)
                        styledTr {
                            key = fileInfo.path

                            css {
                                cursor = Cursor.pointer
                                hover {
                                    backgroundColor =
                                        if (checked) {
                                            selectedHoverRow
                                        }
                                        else {
                                            hoverRow
                                        }
                                }
                                if (checked) {
                                    backgroundColor = selectedRow
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
                                    attrs["disabled"] = props.editDisabled
                                    attrs["onChange"] = {}
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