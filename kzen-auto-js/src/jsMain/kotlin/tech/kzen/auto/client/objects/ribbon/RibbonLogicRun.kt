package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.NamedColor
import web.cssom.em
import web.cssom.px
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
external interface RibbonLogicRunState: State {
    var runnable: Boolean
    var active: Boolean
    var executing: Boolean
    var dropdownOpen: Boolean
    var frame: LogicRunFrameInfo?

    // Run-start mode: when set, a failed step pauses the run (to fix + continue) instead of ending
    // it. Read at start only; locked while a run is active.
    var pauseOnError: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("ConstPropertyName")
class RibbonLogicRun (
    props: Props
):
    RPureComponent<Props, RibbonLogicRunState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val runningKey = "running"

        private const val actionStep = "step"
        private const val actionRunOrPause = "run-pause"
        private const val actionStop = "stop"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: RefObject<HTMLElement> = createRef()

    // Held outside React state — fresh instances per tick would otherwise defeat shallow shouldComponentUpdate.
    private var mainObjectLocation: ObjectLocation? = null
    private var latestFrame: LogicRunFrameInfo? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun RibbonLogicRunState.init(props: Props) {
        runnable = false
        active = false
        executing = false
        dropdownOpen = false
        frame = null
        pauseOnError = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)

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
        ClientContext.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        val isLogic = AutoConventions.isLogic(documentNotation)
        val clientLogicState = clientState.clientLogicState
        val nextFrame = clientLogicState.logicStatus?.active?.frame

        mainObjectLocation = documentPath.toMainObjectLocation()
        latestFrame = nextFrame

        val dropdownWasOpen = state.dropdownOpen
        setState {
            runnable = isLogic
            active = clientLogicState.isActive()
            executing = clientLogicState.isExecuting()
            if (dropdownWasOpen) {
                frame = nextFrame
            }
        }
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
        val frameAtOpen = latestFrame
        setState {
            dropdownOpen = true
            frame = frameAtOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            dropdownOpen = false
        }
    }


    private fun onTogglePauseOnError() {
        // NB: read the prior value OUTSIDE the setState lambda — the kzen setState lambda runs on an
        // empty object, so `pauseOnError = !pauseOnError` inside it would read undefined.
        val next = !state.pauseOnError
        setState {
            pauseOnError = next
        }
    }


    private fun onAction(action: String, active: Boolean, executing: Boolean) {
        val mainObjectLocation = this.mainObjectLocation
            ?: return

        when (action) {
            actionRunOrPause -> {
                if (executing) {
                    ClientContext.clientLogicGlobal.pauseAsync()
                }
                else if (active) {
                    ClientContext.clientLogicGlobal.continueRunAsync()
                }
                else {
                    ClientContext.clientLogicGlobal.startAndRunAsync(
                        mainObjectLocation, false, state.pauseOnError)
                }
            }

            actionStop -> {
                ClientContext.clientLogicGlobal.stopAsync()
            }

            actionStep -> {
                if (active) {
                    ClientContext.clientLogicGlobal.stepAsync()
                }
                else {
                    ClientContext.clientLogicGlobal.startAndRunAsync(
                        mainObjectLocation, true, state.pauseOnError)
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown action: $action")
            }
        }

//        println("%%%% action: $action")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val clientState = state.clientState
//            ?: return

        div {
            renderControls(/*clientState.clientLogicState*/)
        }

        renderDetailsOverlay()
    }


    private fun ChildrenBuilder.renderControls(
//        clientLogicState: ClientLogicState
    ) {
//        println("#### renderControls: ${clientLogicState.logicStatus?.active?.state}")

        val active = state.active
        val executing = state.executing
        val runnable = state.runnable

        ToggleButtonGroup {
//                value = actionRun
            exclusive = true

            asDynamic()["onChange"] = { _, v ->
                onAction(v as String, active, executing)
            }

            if (!active && !runnable) {
                title = "Current document is not runnable"
                disabled = true
            }

            renderStepButton(active, executing, runnable)
            renderRunPauseButton(active, executing, runnable)
            renderStopButton(active)
        }

        renderPauseOnErrorToggle(active, runnable)
        renderDetailsToggle(active)
    }


    private fun ChildrenBuilder.renderPauseOnErrorToggle(active: Boolean, runnable: Boolean) {
        ToggleButton {
            value = "pauseOnError"
            selected = state.pauseOnError

            // Applies at run start only, so lock it once a run is active.
            disabled = active || !runnable
            size = Size.medium

            sx {
                height = 34.px
                marginLeft = 0.5.em
                color = NamedColor.black
            }

            title = "Pause on error: stop at a failed step so it can be fixed and re-run"

            // ToggleButton's onClick is (event, value) -> Unit; we ignore both and just flip state.
            onClick = { _, _ -> onTogglePauseOnError() }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:warning") {}
            }
        }
    }


    private fun ChildrenBuilder.renderStepButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        ToggleButton {
            value = actionStep

            disabled = !(active && !executing || !active && runnable)

            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Step"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:redo") {}
            }
        }
    }


    private fun ChildrenBuilder.renderRunPauseButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        ToggleButton {
            value = actionRunOrPause
            disabled = !active && !runnable
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            if (executing) {
                title = "Pause"
            }
            else if (runnable) {
                title =
                    if (active) {
                        "Continue running"
                    }
                    else {
                        "Run"
                    }
            }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                if (executing) {
                    icon("material-symbols:pause") {}
                }
                else {
                    icon("material-symbols:play-arrow") {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderStopButton(active: Boolean) {
        ToggleButton {
            value = actionStop
            disabled = !active
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Stop"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:stop") {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDetailsToggle(active: Boolean) {
        span {
            ref = dropdownAnchorRef

            title =
                if (active) {
                    "Run details"
                }
                else {
                    "Nothing is running"
                }

            IconButton {
                sx {
                    marginTop = (-13).px

                    if (active) {
                        color = NamedColor.black
                    }
                }

                disabled = !active

                if (active) {
                    onClick = { onOptionsOpen() }
                }

                icon("material-symbols:expand-more") {}
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

                val frame = state.frame

                if (frame == null) {
                    +"<Frame missing>"
                }
                else {
                    renderFrame(frame)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderFrame(frame: LogicRunFrameInfo) {
        +"${frame.objectLocation.documentPath.name}"

        val dependencies = frame.dependencies
        if (dependencies.size == 1) {
            hr {}
            renderFrame(dependencies.single())
        }
        else if (dependencies.size > 1) {
            for (dependency in dependencies) {
                div {
                    key = Key(dependency.objectLocation.asString())

                    css {
                        marginLeft = 0.5.em
                    }

                    hr {}

                    renderFrame(dependency)
                }
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