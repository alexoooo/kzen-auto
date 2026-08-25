package tech.kzen.auto.client.objects.document.report.input.browse

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.file.FileBrowser
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.platform.collect.toPersistentSet
import web.cssom.LineStyle
import web.cssom.em
import web.cssom.pct


external interface InputBrowserControllerProps: Props {
    var mainLocation: ObjectLocation
    var spec: InputBrowserSpec
    var selectedDataLocation: Set<DataLocation>
    var open: Boolean
    var forceOpen: Boolean
    var inputBrowserState: InputBrowserState
    var inputStore: ReportInputStore
}


external interface InputBrowserControllerState: State {
    var requestPending: Boolean
}


/** Report adapter for the shared callback-driven file-browser presentation. */
class InputBrowserController(
    props: InputBrowserControllerProps
):
    RPureComponent<InputBrowserControllerProps, InputBrowserControllerState>(props)
{
    override fun InputBrowserControllerState.init(props: InputBrowserControllerProps) {
        requestPending = false
    }


    override fun componentDidUpdate(
        prevProps: InputBrowserControllerProps,
        prevState: InputBrowserControllerState,
        snapshot: Any
    ) {
        if (props.open && props.inputBrowserState.browserInfo == null && !state.requestPending) {
            setState { requestPending = true }
        }
        if (state.requestPending && !prevState.requestPending) {
            props.inputStore.browser.browserLoadInfoAsync()
        }
    }


    override fun ChildrenBuilder.render() {
        if (!props.open) {
            return
        }

        val info = props.inputBrowserState.browserInfo
        val directory = props.inputBrowserState.browserDirChangeRequest
            ?: info?.browseDir
            ?: props.spec.directory

        if (!props.forceOpen) {
            div {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = LineStyle.solid
                    width = 100.pct
                    fontSize = 1.5.em
                }
                +"Browser"
            }
        }

        FileBrowser::class.react {
            this.directory = directory
            filter = props.spec.filter
            listing = info?.files
            loading = props.inputBrowserState.browserInfoLoading
            error = props.inputBrowserState.browserInfoError
            checked = props.inputBrowserState.browserChecked
            selected = props.selectedDataLocation
            onDirectorySelected = { props.inputStore.browser.browserDirSelectedAsync(it) }
            onFilterChanged = { props.inputStore.browser.browserFilterUpdateAsync(it) }
            onCheckedChanged = { props.inputStore.browser.browserCheckedUpdate(it.toPersistentSet()) }
            onAdd = { props.inputStore.selected.selectionAddAsync(it) }
            onRemove = { props.inputStore.selected.selectionRemoveAsync(it) }
        }
    }
}
