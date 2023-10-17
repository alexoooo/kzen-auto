package tech.kzen.auto.client.objects.ribbon
//
//import emotion.react.css
//import mui.material.IconButton
//import mui.material.Menu
//import mui.material.MenuItem
//import react.*
//import react.dom.html.ReactHTML.a
//import react.dom.html.ReactHTML.div
//import react.dom.html.ReactHTML.hr
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.service.global.SessionGlobal
//import tech.kzen.auto.client.service.global.SessionState
//import tech.kzen.auto.client.util.NavigationRoute
//import tech.kzen.auto.client.wrap.RPureComponent
//import tech.kzen.auto.client.wrap.material.PlayArrowIcon
//import tech.kzen.auto.client.wrap.setState
//import tech.kzen.auto.common.objects.document.script.ScriptDocument
//import tech.kzen.auto.common.util.RequestParams
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.structure.notation.GraphNotation
//import web.cssom.Globals
//import web.cssom.NamedColor
//import web.cssom.em
//import web.cssom.pct
//import web.html.HTMLElement
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface RibbonRunState: State {
//    var clientState: SessionState?
//    var dropdownOpen: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class RibbonRun (
//    props: Props
//):
//    RPureComponent<Props, RibbonRunState>(props),
//    SessionGlobal.Observer
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        const val runningKey = "running"
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private var dropdownAnchorRef: RefObject<HTMLElement> = createRef()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RibbonRunState.init(props: Props) {
////        active = setOf()
////        selectedFramePaths = listOf()
////        executionModel = null
//        clientState = null
//        dropdownOpen = false
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun componentDidMount() {
//        ClientContext.sessionGlobal.observe(this)
//
////        async {
////            ClientContext.executionRepository.observe(this)
////
////            val initialActiveScripts =
////                    ClientContext.restClient.runningHosts()
////
////            val nextActive = state.active + initialActiveScripts
////
////            setState {
////                active = nextActive
////            }
////        }
//    }
//
//
//    override fun componentWillUnmount() {
////        ClientContext.executionRepository.unobserve(this)
//        ClientContext.sessionGlobal.unobserve(this)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun onClientState(clientState: SessionState) {
//        setState {
//            this.clientState = clientState
////            active = clientState.runningHosts
////            selectedFramePaths = clientState.imperativeModel?.frames?.map { it.path } ?: listOf()
//        }
//
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
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
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
//
//
//    private fun onOptionsOpen() {
//        setState {
//            dropdownOpen = true
//        }
//    }
//
//
//    private fun onOptionsClose() {
////        console.log("^^^^^^ onOptionsClose")
//        setState {
//            dropdownOpen = false
////            hoverItem = false
////            hoverOptions = false
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun ChildrenBuilder.render() {
//        val clientState = state.clientState
//                ?: return
//
//        val active = clientState.runningHosts
//
////        val selected = props.parameters.get(runningKey)?.let { DocumentPath.parse(it) }
//        val selected = clientState.activeHost
//        val selectedFramePaths = clientState.imperativeModel?.frames?.map { it.path } ?: listOf()
//
//        div {
//            ref = dropdownAnchorRef
//
//            IconButton {
//                if (active.isEmpty()) {
//                    title = "No scripts running"
////                        onClick = ::onOptionsOpen
//                }
//                else {
//                    title = "Running"
//                    onClick = { onOptionsOpen() }
//
//                    css {
//                        color = NamedColor.black
//                    }
//                }
//
//                PlayArrowIcon::class.react {}
//            }
//        }
//
//        Menu {
//            open = state.dropdownOpen
//
//            onClose = ::onOptionsClose
//
//            anchorEl = dropdownAnchorRef.current?.let { { _ -> it } }
////                if (dropdownAnchorRef.current != null) {
////                    { _ -> dropdownAnchorRef.current!! }
////                }
////                else {
////                    null
////                }
//
//            div {
//                css {
//                    width = 16.em
//                }
//
//                renderSelected(selected, selectedFramePaths)
//
//                hr {}
//
//                renderActiveSelection(
//                        selected,
//                        clientState.graphDefinitionAttempt.graphStructure.graphNotation,
//                        active,
//                        clientState.navigationRoute)
//            }
//        }
//    }
//
//
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
//}