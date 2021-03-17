package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.*
import kotlinx.css.properties.TextDecorationLine
import kotlinx.css.properties.textDecoration
import org.w3c.dom.HTMLElement
import react.*
import react.dom.hr
import styled.css
import styled.styledA
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation


class RibbonRun (
        props: Props
):
        RPureComponent<RibbonRun.Props, RibbonRun.State>(props),
        SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val runningKey = "running"
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//            var actionTypes: List<ObjectLocation>,
//            var ribbonGroups: List<RibbonGroup>,

//            var navPath: DocumentPath?,
//            var parameters: RequestParams,
//
//            var notation: GraphNotation
    ): RProps


    class State(
//            var active: Set<DocumentPath>,
//            var selectedFramePaths: List<DocumentPath>,
//            var executionModel: ImperativeModel?,

            var clientState: SessionState?,

            var dropdownOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: HTMLElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
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

        if (clientState.activeHost == null &&
                clientState.imperativeModel?.isActive() == true)
        {
            onInitialRunning(clientState.imperativeModel.frames[0].path)
        }
        else if (clientState.activeHost != null &&
                clientState.imperativeModel?.isActive() != true)
        {
            onStoppedRunning()
        }
    }


//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
//
//    }


//    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {
//        val active = state.active
//
//        val modifiedActive =
//                if (executionModel.isActive()) {
//                    active + host
//                }
//                else {
//                    active - host
//                }
//
////        console.log("^^^ onExecutionModel: " +
////                "$active / $modifiedActive - $host - ${executionModel.isActive()} - $executionModel")
//
//        val selected = props.parameters.get(runningKey)?.let { DocumentPath.parse(it) }
//
//        val selectedFramePaths =
//                if (executionModel.frames.firstOrNull()?.path == selected) {
//                    executionModel.frames.map { it.path }
//                }
//                else {
//                    state.selectedFramePaths
//                }
//
//        if (active != modifiedActive ||
//                state.selectedFramePaths != selectedFramePaths)
//        {
//            setState {
//                this.active = modifiedActive
//                this.selectedFramePaths = selectedFramePaths
//            }
//
//            if (props.parameters.values.isEmpty()) {
//                onInitialRunning(host)
//            }
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onInitialRunning(host: DocumentPath) {
        ClientContext.navigationGlobal.parameterize(
            RequestParams(
                mapOf(runningKey to listOf(host.asString()))
        )
        )
    }


    private fun onStoppedRunning() {
        ClientContext.navigationGlobal.parameterize(
            RequestParams(
                mapOf()
        )
        )
    }


    private fun onOptionsOpen() {
        setState {
            dropdownOpen = true
        }
    }


    private fun onOptionsClose() {
//        console.log("^^^^^^ onOptionsClose")
        setState {
            dropdownOpen = false
//            hoverItem = false
//            hoverOptions = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
                ?: return

        val active = clientState.runningHosts

//        val selected = props.parameters.get(runningKey)?.let { DocumentPath.parse(it) }
        val selected = clientState.activeHost
        val selectedFramePaths = clientState.imperativeModel?.frames?.map { it.path } ?: listOf()

        styledDiv {
            ref {
                dropdownAnchorRef = it as? HTMLElement
            }

//            +"[Run]"

            child(MaterialIconButton::class) {
                attrs {
                    if (active.isEmpty()) {
                        title = "No scripts running"
//                        onClick = ::onOptionsOpen
                    }
                    else {
                        title = "Running"
                        onClick = ::onOptionsOpen

                        style = reactStyle {
                            color = Color.black
                        }
                    }
                }

                child(PlayArrowIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.dropdownOpen

//                onClose = ::onOptionsCancel
                onClose = ::onOptionsClose

                anchorEl = dropdownAnchorRef
            }

            styledDiv {
                css {
                    width = 16.em
                }

                renderSelected(selected, selectedFramePaths)

                hr {}

                renderActiveSelection(
                        selected,
                        clientState.graphDefinitionAttempt.successful.graphStructure.graphNotation,
                        active,
                        clientState.navigationRoute)
            }
        }
    }


    private fun RBuilder.renderSelected(
            selected: DocumentPath?,
            selectedFramePaths: List<DocumentPath>
    ) {
        if (selected == null) {
            +"Please select a running script (below)"
            return
        }

        +"Selected: ${selected.name}"

        for (framePath in selectedFramePaths) {
            styledDiv {
                attrs {
                    key = framePath.asString()
                }

                +framePath.name.value
            }
        }
    }


    private fun RBuilder.renderActiveSelection(
            selected: DocumentPath?,
            graphNotation: GraphNotation,
            active: Set<DocumentPath>,
            navigationRoute: NavigationRoute
    ) {
        val scriptDocuments = graphNotation
                .documents
                .values
                .filter { ScriptDocument.isScript(/*it.key,*/ it.value) }

        for (script in scriptDocuments) {
            if (! active.contains(script.key) ||
                    selected == script.key) {
                continue
            }

            val pathValue = script.key.asString()

            styledA {
                css {
                    color = Color.inherit
                    textDecoration(TextDecorationLine.initial)
                    width = 100.pct
                    height = 100.pct
                }

                attrs {
                    key = pathValue
                    href = NavigationRoute(
//                            navigationRoute.documentPath,
                            script.key,
                            navigationRoute.requestParams.set(runningKey, pathValue)
                    ).toFragment()
                }

                child(MaterialMenuItem::class) {
                    +script.key.name.value
                }
            }
        }
    }
}