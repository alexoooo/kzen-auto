package tech.kzen.auto.client.objects.ribbon

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.*
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.ReactNode
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconClassForName
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.platform.decodeURIComponent
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface RibbonControllerProps: react.Props {
    var actionTypes: List<ObjectLocation>
    var ribbonGroups: List<RibbonGroup>
}


external interface RibbonControllerState: react.State {
    var updatePending: Boolean
    var documentPath: DocumentPath?
    var parameters: RequestParams

    var type: ObjectLocation?
    var tabIndex: Int

    var currentRibbonGroups: List<RibbonGroup>

    var notation: GraphNotation?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class RibbonController(
    props: RibbonControllerProps
):
    RPureComponent<RibbonControllerProps, RibbonControllerState>(props),
    InsertionGlobal.Subscriber,
    NavigationGlobal.Observer,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val actionTypes: List<ObjectLocation>,
        private val ribbonGroups: List<RibbonGroup>
    ): ReactWrapper<RibbonControllerProps> {
        override fun ChildrenBuilder.child(block: RibbonControllerProps.() -> Unit) {
            RibbonController::class.react {
                actionTypes = this@Wrapper.actionTypes
                ribbonGroups = this@Wrapper.ribbonGroups
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RibbonControllerState.init(props: RibbonControllerProps) {
        documentPath = null
        parameters = RequestParams.empty
        updatePending = false

        type = null
        tabIndex = 0
        currentRibbonGroups = listOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.insertionGlobal.subscribe(this)
        ClientContext.navigationGlobal.observe(this)
        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.navigationGlobal.unobserve(this)
        ClientContext.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: RibbonControllerProps,
            prevState: RibbonControllerState,
            snapshot: Any
    ) {
        if (//state.documentPath == prevState.documentPath &&
                ! state.updatePending) {
            return
        }

        if (state.documentPath == null) {
            setState {
                updatePending = false
                type = null
                tabIndex = 0
                currentRibbonGroups = listOf()
            }
        }
        else {
//            console.log("^^^^^ 00!! - ${state.documentPath} - ${state.notation}")
            val notation = state.notation
                ?: return

            val typeName = DocumentArchetype.archetypeName(
                notation, state.documentPath!!
            ) ?: return


            val documentRibbonGroups = props
                    .ribbonGroups
                    .filter { it.archetype.objectPath.name == typeName }

//            console.log("^^^^^ 00!! - typeName $typeName - $documentRibbonGroups")

            setState {
                updatePending = false
                tabIndex = 0
                currentRibbonGroups = documentRibbonGroups
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onInsertionSelected(action: ObjectLocation) {
        setState {
            type = action
        }
    }


    override fun onInsertionUnselected() {
        setState {
            type = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
            documentPath: DocumentPath?,
            parameters: RequestParams
    ) {
        setState {
            this.updatePending = true
            this.documentPath = documentPath
            this.parameters = parameters
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            notation = graphDefinition.graphStructure.graphNotation
            updatePending = true
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            notation = graphDefinition.graphStructure.graphNotation
            updatePending = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onUnSelect() {
        ClientContext.insertionGlobal.clearSelection()
    }


    private fun onSelect(actionType: ObjectLocation) {
        ClientContext.insertionGlobal.setSelected(actionType)
    }


    private fun onTab(index: Int) {
        setState {
            tabIndex = index
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        renderTabs()

        div {
            css {
                marginTop = 0.5.em
            }
            renderSubActions()
        }
    }


    private fun ChildrenBuilder.renderLogo() {
        a {
            href = "/"

            img {
                css {
                    height = 42.px
                }

                src = "logo.png"
                title = "Kzen (home)"
            }
        }
    }


    private fun ChildrenBuilder.renderTabs() {
        Tabs {
            textColor = TabsTextColor.inherit
            indicatorColor = TabsIndicatorColor.primary

            value = state.tabIndex

            onChange = { _, index: Int ->
                onTab(index)
            }

            for (ribbonGroup in state.currentRibbonGroups) {
                Tab {
                    key = ribbonGroup.title
                    label = ReactNode(ribbonGroup.title)
                }
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


    private fun ChildrenBuilder.renderSubActions() {
//        +"SubActions"
        if (state.currentRibbonGroups.isEmpty()) {
            return
        }

        val currentRibbon = state.currentRibbonGroups[state.tabIndex]

        for (ribbonTool in currentRibbon.children) {
            Button {
                key = ribbonTool.delegate.asString()
                variant = ButtonVariant.outlined
                size = Size.small

                onClick = {
                    if (state.type == ribbonTool.delegate) {
                        onUnSelect()
                    }
                    else {
                        onSelect(ribbonTool.delegate)
                    }
                }

                sx {
                    if (state.type == ribbonTool.delegate) {
                        color = NamedColor.white
                        backgroundColor = Color("#649fff")
                    }

                    marginRight = 0.5.em
                    marginBottom = 0.5.em
                    color = NamedColor.black
                    borderColor = Color("#c4c4c4")
                }

                val description = state.notation
                        ?.firstAttribute(ribbonTool.delegate, AutoConventions.descriptionAttributePath)
                        ?.asString()

                if (description != null) {
                    this.title = description
                }

                val icon = state.notation
                        ?.firstAttribute(ribbonTool.delegate, AutoConventions.iconAttributePath)
                        ?.asString()

                if (icon != null) {
                    iconClassForName(icon).react {
                        style = jso {
                            marginRight = 0.25.em
                        }
                    }
                }

                val title = state.notation
                        ?.firstAttribute(ribbonTool.delegate, AutoConventions.titleAttributePath)
                        ?.asString()
                        ?: ribbonTool.delegate.objectPath.name.value

                +title
            }
        }
    }
}
