package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.Color
import kotlinx.css.color
import org.w3c.dom.HTMLElement
import react.*
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
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
    class Props(
//            var actionTypes: List<ObjectLocation>,
//            var ribbonGroups: List<RibbonGroup>,
//
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

                child(MaterialMenuItem::class) {
                    attrs {
                        key = script.key.asString()
//                        onClick = {
//                            onAdd(archetypeLocation, title)
//                        }
                    }

                    +script.key.name.value
                }
            }
        }
    }
}