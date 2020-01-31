package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.*
import kotlinx.html.title
import react.*
import styled.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionGlobal
import tech.kzen.auto.client.service.NavigationRepository
import tech.kzen.auto.client.util.decodeURIComponent
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


@Suppress("unused")
class RibbonController(
        props: Props
):
        RPureComponent<RibbonController.Props, RibbonController.State>(props),
        InsertionGlobal.Subscriber,
        NavigationRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var actionTypes: List<ObjectLocation>,
            var ribbonGroups: List<RibbonGroup>,

            var notation: GraphNotation
    ): RProps


    class State(
            var updatePending: Boolean,
            var documentPath: DocumentPath?,

            var type: ObjectLocation?,
            var tabIndex: Int = 0,

            var currentRibbonGroups: List<RibbonGroup>
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val actionTypes: List<ObjectLocation>,
            private val ribbonGroups: List<RibbonGroup>
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(RibbonController::class) {
                attrs {
                    actionTypes = this@Wrapper.actionTypes
                    ribbonGroups = this@Wrapper.ribbonGroups
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        documentPath = null
        updatePending = false

        type = null
        tabIndex = 0
        currentRibbonGroups = listOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.insertionGlobal.subscribe(this)
        ClientContext.navigationRepository.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.navigationRepository.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        if (//state.documentPath == prevState.documentPath &&
                ! state.updatePending) {
            return
        }

//        console.log("^^^^^ handleNavigation !!!",
//                state.updatePending,
//                state.documentPath,
//                state.currentRibbonGroups,
//                prevState.documentPath,
//                prevState.currentRibbonGroups)

        if (state.documentPath == null) {
            setState {
                updatePending = false
                type = null
                tabIndex = 0
                currentRibbonGroups = listOf()
            }
        }
        else {
            val typeName = DocumentArchetype.archetypeName(props.notation, state.documentPath!!)
                    ?: return

//            console.log("^^^^^ handleNavigation - ribbonGroups", typeName, props.ribbonGroups)

            val documentRibbonGroups = props
                    .ribbonGroups
                    .filter { it.archetype.objectPath.name == typeName }

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
    override fun handleNavigation(documentPath: DocumentPath?) {
        setState {
            this.updatePending = true
            this.documentPath = documentPath
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
    override fun RBuilder.render() {
        styledDiv {
            css {
                backgroundColor = Color.white
                paddingRight = 1.75.em
                paddingBottom = 1.px
                paddingLeft = 1.75.em
            }

            styledSpan {
                css {
                    float = Float.left
                    marginLeft = (-11).px
                    marginTop = 7.px
                    marginRight = 1.em
                }
                renderLogo()
            }

            styledDiv {
                css {
                    float = Float.right
                }
                renderRightFloat()
            }

            renderTabs()

            styledDiv {
                css {
                    marginTop = 0.5.em
                }
                renderSubActions()
            }
        }
    }


    private fun RBuilder.renderLogo() {
        styledA {
            attrs {
                href = "/"
            }

            styledImg(src = "logo.png") {
                css {
                    height = 42.px
                }

                attrs {
                    title = "Kzen (home)"
                }
            }
        }
    }


    private fun RBuilder.renderTabs() {
        child(MaterialTabs::class) {
            attrs {
                textColor = "primary"
                indicatorColor = "primary"

                value = state.tabIndex

                onChange = { _, index: Int ->
                    onTab(index)
                }
            }

            for (ribbonGroup in state.currentRibbonGroups) {
                child(MaterialTab::class) {
                    attrs {
                        key = ribbonGroup.title
                        label = ribbonGroup.title
                    }
                }
            }
        }
    }


    private fun RBuilder.renderRightFloat() {
        renderTitle()

        renderRunNavigation()
    }


    private fun RBuilder.renderTitle() {
        val projectTitle =
                if (ClientContext.baseUrl.isEmpty()) {
                    "Running in dev mode"
                }
                else {
                    decodeURIComponent(ClientContext.baseUrl).substringAfter("/")
                }

        styledDiv {
            css {
                marginTop = 0.5.em
                marginRight = 0.5.em
                fontSize = 1.5.em
                color = Color.gray
                fontStyle = FontStyle.italic
                display = Display.inlineBlock
            }

            attrs {
                title = "Project name"
            }

            +projectTitle
        }
    }


    private fun RBuilder.renderRunNavigation() {
        styledDiv {
            css {
                display = Display.inlineBlock
            }

            child(RibbonRun::class) {
                attrs {
                    notation = props.notation
                }
            }
        }
    }


    private fun RBuilder.renderSubActions() {
        if (state.currentRibbonGroups.isEmpty()) {
            return
        }

        val currentRibbon = state.currentRibbonGroups[state.tabIndex]

        for (ribbonTool in currentRibbon.children) {
            child(MaterialButton::class) {
                attrs {
                    key = ribbonTool.delegate.asString()
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        if (state.type == ribbonTool.delegate) {
                            onUnSelect()
                        }
                        else {
                            onSelect(ribbonTool.delegate)
                        }
                    }

                    style = reactStyle {
                        if (state.type == ribbonTool.delegate) {
                            color = Color.white
                            backgroundColor = Color("#649fff")
                        }

                        marginRight = 0.5.em
                        marginBottom = 0.5.em
                    }
                }

                val description = props.notation
                        .transitiveAttribute(ribbonTool.delegate, AutoConventions.descriptionAttributePath)
                        ?.asString()

                if (description != null) {
                    attrs {
                        this.title = description
                    }
                }

                val icon = props.notation
                        .transitiveAttribute(ribbonTool.delegate, AutoConventions.iconAttributePath)
                        ?.asString()

                if (icon != null) {
                    child(iconClassForName(icon)) {
                        attrs {
                            style = reactStyle {
                                marginRight = 0.25.em
                            }
                        }
                    }
                }

                val title = props.notation
                        .transitiveAttribute(ribbonTool.delegate, AutoConventions.titleAttributePath)
                        ?.asString()
                        ?: ribbonTool.delegate.objectPath.name.value

                +title
            }
        }
    }
}
