package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.Color
import kotlinx.css.color
import org.w3c.dom.HTMLElement
import react.*
import styled.styledDiv
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.lib.common.model.structure.notation.GraphNotation


class RibbonRun (
        props: Props
):
        RPureComponent<RibbonRun.Props, RibbonRun.State>(props)//,
//        InsertionManager.Observer,
//        NavigationManager.Observer
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

            var dropdownOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: HTMLElement? = null


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
                    title = "Run"
                    onClick = ::onOptionsOpen

                    style = reactStyle {
                        color = Color.black
//                        marginTop = (-13).px
//                        marginRight = (-16).px
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