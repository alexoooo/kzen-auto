package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.*
import kotlinx.css.properties.TextDecorationLine
import kotlinx.css.properties.textDecoration
import org.w3c.dom.HTMLElement
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledA
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


class RibbonRun (
        props: Props
):
        RPureComponent<RibbonRun.Props, RibbonRun.State>(props),
        ExecutionRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val runningKey = "running"
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//            var actionTypes: List<ObjectLocation>,
//            var ribbonGroups: List<RibbonGroup>,

            var navPath: DocumentPath?,
            var parameters: RequestParams,

            var notation: GraphNotation
    ): RProps


    class State(
//            var updatePending: Boolean,
//            var documentPath: DocumentPath?,
//
//            var type: ObjectLocation?,
//            var tabIndex: Int = 0,
//
//            var currentRibbonGroups: List<RibbonGroup>

            var active: Set<DocumentPath>,
//            var executionModel: ImperativeModel?,

            var dropdownOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: HTMLElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        active = setOf()
//        executionModel = null
        dropdownOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.executionRepository.observe(this)

            val initialActiveScripts =
                    ClientContext.restClient.activeScripts()

            val nextActive = state.active + initialActiveScripts

            setState {
                active = nextActive
            }
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionRepository.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {

    }


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {
        val active = state.active

        val modifiedActive =
                if (executionModel.isActive()) {
                    active + host
                }
                else {
                    active - host
                }

//        console.log("^^^ onExecutionModel: " +
//                "$active / $modifiedActive - $host - ${executionModel.isActive()} - $executionModel")

        if (active != modifiedActive) {
//            console.log("!! setting: $modifiedActive")
            setState {
                this.active = modifiedActive
            }
        }

//        setState {
//            this.executionModel = executionModel
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
        val selected = props.parameters.get(runningKey)

        styledDiv {
            ref {
                dropdownAnchorRef = it as? HTMLElement
            }

//            +"[Run]"

            child(MaterialIconButton::class) {
                attrs {
                    if (state.active.isEmpty()) {
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

            val scriptDocuments = props
                    .notation
                    .documents
                    .values
                    .filter { ScriptDocument.isScript(it.key, it.value) }

            for (script in scriptDocuments) {
                if (! state.active.contains(script.key)) {
                    continue
                }

                val pathValue = script.key.asString()
                val isActive = selected == pathValue

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
                                props.navPath,
                                props.parameters.set(runningKey, pathValue)
                        ).toFragment()
                    }

                    child(MaterialMenuItem::class) {
                        if (isActive) {
                            +"> "
                        }
                        +script.key.name.value
                    }
                }
            }
        }
    }
}