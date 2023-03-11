package tech.kzen.auto.client.objects.ribbon

import csstype.*
import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.platform.decodeURIComponent
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface HeaderControllerProps: react.Props {
    var documentControllers: List<DocumentController>
}


external interface HeaderControllerState: react.State {
    var structure: GraphStructure?
    var documentPath: DocumentPath?
    var transition: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class HeaderController(
    props: HeaderControllerProps
):
    RPureComponent<HeaderControllerProps, HeaderControllerState>(props),
    LocalGraphStore.Observer,
    NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val documentControllers: List<DocumentController>
    ): ReactWrapper<HeaderControllerProps> {
        override fun ChildrenBuilder.child(block: HeaderControllerProps.() -> Unit) {
            HeaderController::class.react {
                documentControllers = this@Wrapper.documentControllers
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun HeaderControllerState.init(props: HeaderControllerProps) {
        structure = null
        documentPath = null
        transition = false
    }


    override fun componentDidUpdate(
        prevProps: HeaderControllerProps,
        prevState: HeaderControllerState,
        snapshot: Any
    ) {
        if (state.documentPath != prevState.documentPath &&
                state.documentPath != null &&
                prevState.documentPath != null
        ) {
            setState {
                transition = true
            }
        }

        if (state.transition) {
            setState {
                transition = false
            }
        }
    }


    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.graphStructure
        }
    }


    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun documentArchetypeName(): ObjectName? {
        val notation = state.structure?.graphNotation
            ?: return null

        val path = state.documentPath
            ?: return null

        return DocumentArchetype.archetypeName(notation, path)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                backgroundColor = NamedColor.white
                paddingRight = 1.75.em
                paddingBottom = 1.px
                paddingLeft = 1.75.em
                minHeight = 55.px
            }

            span {
                css {
                    float = Float.left
                    marginLeft = (-11).px
                    marginTop = 7.px
                    marginRight = 1.em
                }
                renderLogo()
            }

            div {
                css {
                    float = Float.right
                }
                renderRightFloat()
            }

            if (! state.transition) {
                val archetypeName = documentArchetypeName()
                if (archetypeName != null) {
                    renderHeaderController(archetypeName)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderLogo() {
        a {
            href = "/"

            img {
                src = "$staticResourcePath/logo.png"

                css {
                    height = 42.px
                }

                title = "Kzen (home)"
            }
        }
    }


    private fun ChildrenBuilder.renderRightFloat() {
        renderTitle()
        renderRunNavigation()
    }


    private fun ChildrenBuilder.renderTitle() {
        val projectTitle =
            if (ClientContext.baseUrl.isEmpty()) {
                "Running in dev mode"
            }
            else {
                decodeURIComponent(ClientContext.baseUrl).substringAfter("/")
            }

        div {
            css {
                marginTop = 0.5.em
                marginRight = 0.5.em
                fontSize = 1.5.em
                color = NamedColor.gray
                fontStyle = FontStyle.italic
                display = Display.inlineBlock
            }

            title = "Project name"

            +projectTitle
        }
    }


    private fun ChildrenBuilder.renderRunNavigation() {
        div {
            css {
                display = Display.inlineBlock
            }

            RibbonRun::class.react {
//                navPath = state.documentPath
//                parameters = state.parameters
//                notation = props.notation
            }
        }
    }


    private fun ChildrenBuilder.renderHeaderController(
        archetypeName: ObjectName
    ) {
        val documentController = props.documentControllers
            .singleOrNull { archetypeName == it.archetypeLocation().objectPath.name }

        if (documentController == null) {
            +"Header: $archetypeName"
        }
        else {
            documentController.header().child(this) {}
        }
    }
}