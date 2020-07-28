package tech.kzen.auto.client.objects.document.process

import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.MaterialFab
import tech.kzen.auto.client.wrap.MenuBookIcon
import tech.kzen.auto.client.wrap.reactStyle


class ProcessRun(
    props: Props
):
    RPureComponent<ProcessRun.Props, ProcessRun.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState
    ): RProps


    class State(
        var fabHover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        fabHover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOuterEnter() {
        setState {
            fabHover = true
        }
    }


    private fun onOuterLeave() {
        setState {
            fabHover = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        div {
            attrs {
                onMouseOverFunction = {
                    onOuterEnter()
                }
                onMouseOutFunction = {
                    onOuterLeave()
                }
            }

            renderInner()
        }
    }


    private fun RBuilder.renderInner() {
        renderSecondaryActions()
        renderMainAction()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction() {
        child(MaterialFab::class) {
            child(MenuBookIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 3.em
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSecondaryActions() {
        +"x"
    }
}