package tech.kzen.auto.client.objects.document.report.input.browse

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.attrs
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
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
        val hoverRow = Color("rgb(220, 220, 220)")
        private val selectedRow = Color("rgb(220, 220, 255)")
        private val selectedHoverRow = Color("rgb(190, 190, 240)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
        var browserOpen: Boolean
    }


    interface State: RState {
//        var browserOpen: Boolean
        var requestedInitial: Boolean
        var selected: PersistentSet<DataLocation>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        browserOpen = false
        requestedInitial = false
        selected = persistentSetOf()
    }


//    override fun componentDidMount() {
//        console.log("^^^^ componentDidMount - ${props.browserOpen} - ${state.requestedInitial} - ${props.reportState.inputBrowser}")
//        if (props.browserOpen && props.reportState.inputBrowser == null && ! state.requestedInitial) {
//            console.log("^^^^ componentDidMount - request")
//
//            setState {
//                requestedInitial = true
//            }
//            onBrowseRefresh()
//        }
//    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        console.log("^^^ componentDidUpdate - ${props.browserOpen}")

        if (props.reportState.isInitiating() || ! props.browserOpen) {
//            console.log("^^^ INIT")
            return
        }

        if (! state.requestedInitial && props.reportState.inputBrowser == null) {
            setState {
                requestedInitial = true
            }
        }
        else if (state.requestedInitial && ! prevState.requestedInitial) {
            onBrowseRefresh()
        }

        if (props.reportState.inputBrowser != prevProps.reportState.inputBrowser &&
                ! state.selected.isEmpty()
        ) {
            val available = props.reportState.inputBrowser?.map { it.path }?.toSet() ?: setOf()
            setState {
                selected = selected.filter { it in available }.toPersistentSet()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onBrowseRefresh() {
//        console.log("&&& onBrowseRefresh")
        props.dispatcher.dispatchAsync(ListInputsBrowserRequest)
    }


//    private fun onToggleBrowser() {
////        console.log("&&& onToggleBrowser")
//        setState {
//            browserOpen = ! browserOpen
//        }
//    }


    private fun onDirSelected(dir: DataLocation) {
        props.dispatcher.dispatchAsync(ListInputsBrowserNavigate(dir))
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
                        .inputBrowser!!
                        .filter { ! it.directory }
                        .map { it.path }
                        .toPersistentSet()
                }
        }
    }


    private fun onAddToSelection() {
        val addedPaths = newSelectedPaths()

        async {
            val effects = props.dispatcher.dispatch(PluginPathInfoRequest(addedPaths))

            val pathDataSpecs = effects.filterIsInstance<PluginPathInfoResult>().first().paths
                ?: return@async

            props.dispatcher.dispatch(InputsSelectionAddRequest(pathDataSpecs))
        }
    }


    private fun onRemoveFromSelection() {
        val removedPaths = existingSelectedPaths()

        val dataLocationsSet = removedPaths.toSet()
        val inputSelectionSpec = props.reportState.inputSpec().selection
        val removedSpecs = inputSelectionSpec.locations.filter { it.location in dataLocationsSet }

        props.dispatcher.dispatchAsync(InputsSelectionRemoveRequest(removedSpecs))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun newSelectedPaths(): List<DataLocation> {
        if (state.selected.isEmpty()) {
            return listOf()
        }

        val selectedSet = props.reportState.selectedPathSet()
        return state.selected.filter { it !in selectedSet }
    }


    private fun existingSelectedPaths(): List<DataLocation> {
        if (state.selected.isEmpty()) {
            return listOf()
        }

        val selectedSet = props.reportState.selectedPathSet()
        return state.selected.filter { it in selectedSet }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (! props.browserOpen) {
            return
        }

        val listingSelected = props.reportState.inputSelection
        val browserListing = props.reportState.inputBrowser
        val browserDir = props.reportState.inputBrowseDir
        val inputError = props.reportState.inputError

        val forceOpen =
            listingSelected != null && listingSelected.locations.isEmpty()

        if (! forceOpen) {
            styledDiv {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Browser"
            }
        }

        styledDiv {
            if (listingSelected == null && inputError != null) {
                +"Error"
            }
            else if (listingSelected != null && inputError != null) {
                renderPathEditError(props.reportState.inputSpec().browser.directory)
            }
            else if (browserListing == null || browserDir == null) {
                styledDiv {
                    val asString =
                        browserDir?.asString()
                        ?: props.reportState.inputSpec().browser.directory.asString()

                    if (asString != ".") {
                        +asString
                    }
                }
//                +"Loading..."
            }
            else {
                renderDetail(browserListing, browserDir)
            }
        }
    }


    private fun RBuilder.renderDetail(browserListing: List<DataLocationInfo>, browserDir: DataLocation) {
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
        val selectedAddCount = newSelectedPaths().size
        val selectedRemoveCount = existingSelectedPaths().size

        styledDiv {
            styledDiv {
                css {
                    minWidth = 8.em
                    display = Display.inlineBlock
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"

                        style = reactStyle {
                            marginRight = 1.em
                            borderWidth = 2.px
                        }

                        onClick = {
                            onAddToSelection()
                        }

                        if (selectedAddCount == 0) {
                            disabled = true
                            title =
                                if (state.selected.isEmpty()) {
                                    "No files selected"
                                }
                                else {
                                    "No new files selected"
                                }
                        }
                        else if (props.editDisabled) {
                            disabled = true
                            title = "Disabled while running"
                        }
                    }

                    child(AddCircleOutlineIcon::class) {
                        attrs {
                            style = reactStyle {
                                marginRight = 0.25.em
                            }
                        }
                    }

                    if (selectedAddCount == 0) {
                        +"Add"
                    }
                    else {
                        +"Add ($selectedAddCount)"
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"

                        onClick = {
                            onRemoveFromSelection()
                        }

                        if (selectedRemoveCount == 0) {
                            disabled = true
                            title =
                                if (state.selected.isEmpty()) {
                                    "No files selected"
                                }
                                else {
                                    "No existing files selected"
                                }
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

                    if (selectedRemoveCount == 0) {
                        +"Remove"
                    }
                    else {
                        +"Remove ($selectedRemoveCount)"
                    }
                }
            }

            styledSpan {
                css {
                    float = Float.right
                }
                child(InputBrowserFilter::class) {
                    attrs {
                        reportState = props.reportState
                        dispatcher = props.dispatcher
                        editDisabled = props.editDisabled
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFileTable(browserListing: List<DataLocationInfo>) {
        if (browserListing.isEmpty()) {
            styledDiv {
                css {
//                    fontSize = 1.25.em
//                    fontWeight = FontWeight.bold
//                    marginLeft = 0.5.em
                }
                val hasFilter = props.reportState.inputSpec().browser.filter.isNotBlank()
                val filterSuffix = if (hasFilter) { " or adjust filter" } else { "" }
                +"Empty (please select different folder$filterSuffix above)"
            }
            return
        }

        val selectedPathSet = props.reportState.selectedPathSet()
        val (folders, files) = browserListing.partition { it.directory }

        // see: https://stackoverflow.com/questions/1122381/how-to-force-child-div-to-be-100-of-parent-divs-height-without-specifying-pare
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
//                borderWidth = 1.px
                borderWidth = 2.px
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
//                                        disabled = props.editDisabled
                                        disabled = false
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
                styledTbody {
                    for (folderInfo in folders) {
                        styledTr {
                            key = folderInfo.path.asString()

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

                    for (fileInfo in files) {
                        val checked = fileInfo.path in state.selected
                        val selected = fileInfo.path in selectedPathSet
                        styledTr {
                            key = fileInfo.path.asString()

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
        }
    }


    private fun RBuilder.renderPathEditError(browseDir: DataLocation) {
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


    private fun RBuilder.renderPathEdit(browseDir: DataLocation) {
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


//    private fun RBuilder.renderSummary(browseDir: DataLocation?) {
//        styledDiv {
//            css {
//                height = 1.px
//                marginTop = (-4).px
//            }
//        }
////        if (browseDir != null) {
////            +browseDir
////        }
////        else {
////            +props.reportState.inputSpec().browser.directory
////        }
//    }
}