package tech.kzen.auto.client.objects.document.plugin

import kotlinx.css.em
import kotlinx.css.margin
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


@Suppress("unused")
class PluginController(
    props: RProps
):
    RPureComponent<RProps, PluginController.State>(props),
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val separatorWidth = 2.px
//        val separatorColor = Color("#c3c3c3")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
        var clientState: SessionState?
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
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
//        console.log("#!#@!#@! onClientState - ${clientState.imperativeModel}")
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
            ?: return

        val documentPath: DocumentPath = clientState.navigationRoute.documentPath
            ?: return

        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

        styledDiv {
            css {
                margin(2.em)
            }

            child(AttributePathValueEditor::class) {
                attrs {
//                    labelOverride = "Preview Start Row"

                    this.clientState = clientState
                    objectLocation = mainLocation
                    attributePath = PluginConventions.jarPathAttributeName.asAttributePath()

                    valueType = TypeMetadata.long

//                    onChange = {
//                        onPreviewRefresh()
//                    }
                }

//                key = "start-row"
            }

//            +"Plugin fff"
        }
    }
}