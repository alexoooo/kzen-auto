package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.setState
import web.cssom.NamedColor
import web.cssom.em
import web.cssom.px
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
external interface RibbonLogicRunState: State {
    var clientState: SessionState?
    var dropdownOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class RibbonLogicRun (
    props: Props
):
    RPureComponent<Props, RibbonLogicRunState>(props),
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val runningKey = "running"

        private const val actionStep = "step"
        private const val actionRun = "run"
        private const val actionStop = "stop"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: RefObject<HTMLElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun RibbonLogicRunState.init(props: Props) {
//        active = setOf()
//        selectedFramePaths = listOf()
//        executionModel = null
        clientState = null
        dropdownOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)

//        async {
//            ClientContext.executionRepository.observe(this)
//
//            val initialActiveScripts =
//                    ClientContext.restClient.runningHosts()
//
//            val nextActive = state.active + initialActiveScripts
//
//            setState {
//                active = nextActive
//            }
//        }
    }


    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
        ClientContext.sessionGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
//            active = clientState.runningHosts
//            selectedFramePaths = clientState.imperativeModel?.frames?.map { it.path } ?: listOf()
        }

//        if (clientState.activeHost == null &&
//                clientState.imperativeModel?.isActive() == true)
//        {
//            onInitialRunning(clientState.imperativeModel.frames[0].path)
//        }
//        else if (clientState.activeHost != null &&
//                clientState.imperativeModel?.isActive() != true)
//        {
//            onStoppedRunning()
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onInitialRunning(host: DocumentPath) {
//        ClientContext.navigationGlobal.parameterize(
//            RequestParams(
//                mapOf(runningKey to listOf(host.asString())))
//        )
//    }
//
//
//    private fun onStoppedRunning() {
//        ClientContext.navigationGlobal.parameterize(
//            RequestParams(
//                mapOf())
//        )
//    }


    private fun onOptionsOpen() {
        setState {
            dropdownOpen = true
        }
    }


    private fun onOptionsClose() {
        setState {
            dropdownOpen = false
        }
    }


    private fun onAction(action: String) {
        when (action) {
            actionRun -> {
                val mainObjectLocation = state.clientState!!.navigationRoute.documentPath!!.toMainObjectLocation()
                ClientContext.clientLogicGlobal.startAndRunAsync(mainObjectLocation)
            }

            else -> {
                throw IllegalArgumentException("Unknown action: $action")
            }
        }

//        println("%%%% action: $action")
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        div {
            renderControls(clientState.clientLogicState)
        }

        renderDetailsOverlay()
    }


    private fun ChildrenBuilder.renderControls(
        clientLogicState: ClientLogicState
    ) {
        val active = clientLogicState.logicStatus?.active != null
        val runnable = true

        ToggleButtonGroup {
//                value = actionRun
            exclusive = true

            onChange = { _, v ->
                onAction(v as String)
            }

            if (! active && ! runnable) {
                title = "Current document is not runnable"
                disabled = true
            }

            renderStepButton()
            renderRunPauseButton(active, runnable)
            renderStopButton()
        }

        renderDetailsToggle(active)
    }


    private fun ChildrenBuilder.renderStepButton() {
        ToggleButton {
            value = actionStep
            disabled = true
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
                borderWidth = 2.px
            }

            title = "Step"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                RedoIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderRunPauseButton(
        active: Boolean,
        runnable: Boolean
    ) {
        ToggleButton {
            value = actionRun
            disabled = ! active && ! runnable
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
//                borderWidth = 2.px
            }

            if (active) {
                title = "Pause"
            }
            else if (runnable) {
                title = "Run"
            }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                if (active) {
                    PauseIcon::class.react {}
                }
                else {
                    PlayArrowIcon::class.react {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderStopButton() {
        ToggleButton {
            value = actionStop
            disabled = true
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
                borderWidth = 2.px
            }

            title = "Stop"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                StopIcon::class.react {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDetailsToggle(active: Boolean) {
        span {
            ref = dropdownAnchorRef

            title =
                if (active) {
                    "Details"
                }
                else {
                    "Nothing is running"
                }

            IconButton {
                sx {
                    marginTop = (-13).px
                }

                disabled = ! active

                if (active) {
                    onClick = { onOptionsOpen() }

                    sx {
                        color = NamedColor.black
                    }
                }

                ExpandMoreIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderDetailsOverlay() {
        Menu {
            open = state.dropdownOpen

            onClose = ::onOptionsClose

            anchorEl = dropdownAnchorRef.current?.let { { _ -> it } }

            div {
                css {
                    width = 16.em
                }

//                renderSelected(selected, selectedFramePaths)
                +"foo"

                ReactHTML.hr {}

                +"bar"
//                renderActiveSelection(
//                        selected,
//                        clientState.graphDefinitionAttempt.graphStructure.graphNotation,
//                        active,
//                        clientState.navigationRoute)
            }
        }
    }



//    private fun ChildrenBuilder.renderSelected(
//            selected: DocumentPath?,
//            selectedFramePaths: List<DocumentPath>
//    ) {
//        if (selected == null) {
//            +"Please select a running script (below)"
//            return
//        }
//
//        +"Selected: ${selected.name}"
//
//        for (framePath in selectedFramePaths) {
//            div {
//                key = framePath.asString()
//                +framePath.name.value
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderActiveSelection(
//            selected: DocumentPath?,
//            graphNotation: GraphNotation,
//            active: Set<DocumentPath>,
//            navigationRoute: NavigationRoute
//    ) {
//        val scriptDocuments = graphNotation
//                .documents
//                .values
//                .filter { ScriptDocument.isScript(/*it.key,*/ it.value) }
//
//        for (script in scriptDocuments) {
//            if (! active.contains(script.key) ||
//                    selected == script.key) {
//                continue
//            }
//
//            val pathValue = script.key.asString()
//
//            a {
//                css {
//                    color = Globals.inherit
//                    textDecoration = Globals.initial
//                    width = 100.pct
//                    height = 100.pct
//                }
//
//                key = pathValue
//                href = NavigationRoute(
//                    script.key,
//                    navigationRoute.requestParams.set(runningKey, pathValue)
//                ).toFragment()
//
//                MenuItem {
//                    +script.key.name.value
//                }
//            }
//        }
//    }
}