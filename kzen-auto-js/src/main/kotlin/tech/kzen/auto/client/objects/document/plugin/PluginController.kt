package tech.kzen.auto.client.objects.document.plugin

import kotlinx.css.em
import kotlinx.css.margin
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class PluginController(
    props: RProps
):
    RPureComponent<RProps, PluginController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val separatorWidth = 2.px
//        val separatorColor = Color("#c3c3c3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
//        var reportState: ReportState?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(PluginController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                margin(2.em)
            }
            +"Plugin"
        }
    }
}