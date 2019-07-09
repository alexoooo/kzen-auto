package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.*
import kotlinx.html.title
import react.*
import styled.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.model.GraphNotation


@Suppress("unused")
class RibbonController(
        props: Props
):
        RPureComponent<RibbonController.Props, RibbonController.State>(props),
        InsertionManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var actionTypes: List<ObjectLocation>,
            var ribbonGroups: List<RibbonGroup>,

            var notation: GraphNotation
    ): RProps


    class State(
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
        type = null
        tabIndex = 0
        currentRibbonGroups = listOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.insertionManager.subscribe(this)
        ClientContext.navigationManager.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
        setState {
//            name = NameConventions.randomAnonymous()
            type = action
        }
    }


    override fun onUnselected() {
        setState {
            type = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
//        console.log("^^^^^ handleNavigation", documentPath)

        if (documentPath == null) {
            setState {
                type = null
                tabIndex = 0
                currentRibbonGroups = listOf()
            }
            return
        }

        val typeName = DocumentArchetype.archetypeName(props.notation, documentPath)
                ?: return

//        console.log("^^^^^ handleNavigation - typeName", typeName)

        val documentRibbonGroups = props
                .ribbonGroups
                .filter { it.documentArchetype.name() == typeName }

        setState {
            tabIndex = 0
            currentRibbonGroups = documentRibbonGroups
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onUnSelect() {
        ClientContext.insertionManager.clearSelection()
    }


    private fun onSelect(actionType: ObjectLocation) {
        ClientContext.insertionManager.setSelected(actionType)
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

            styledDiv {
                css {
//                    marginTop = 16.px
                    marginTop = 0.5.em
                }
                renderSubActions()
            }
        }
    }


    private fun RBuilder.renderSubActions() {
        if (state.currentRibbonGroups.isEmpty()) {
            return
        }

        val currentRibbon = state.currentRibbonGroups[state.tabIndex]

//        console.log("^^^^^ currentRibbon", currentRibbon)

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
//                            backgroundColor = Color.blue.lighten(50).withAlpha(0.5)
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


    private fun RBuilder.renderLogo() {
        styledA {
            attrs {
                href = "/"
            }

            styledImg(src = "logo.png") {
                css {
//                    height = 52.px
                    height = 42.px
                }

                attrs {
                    title = "Kzen (home)"
                }
            }
        }
    }
}
