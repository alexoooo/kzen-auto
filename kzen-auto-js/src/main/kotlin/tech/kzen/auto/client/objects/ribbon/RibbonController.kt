package tech.kzen.auto.client.objects.ribbon

import kotlinx.css.Color
import kotlinx.css.Float
import kotlinx.css.em
import kotlinx.css.px
import kotlinx.html.title
import react.*
import react.dom.br
import styled.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.action.NameEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.structure.notation.model.GraphNotation


@Suppress("unused")
class RibbonController(
        props: Props
):
        RComponent<RibbonController.Props, RibbonController.State>(props),
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var actionTypes: List<ObjectLocation>,
            var ribbonGroups: List<RibbonGroup>,

            var notation: GraphNotation
    ): RProps


    class State(
            var name: ObjectName?,
            var type: ObjectLocation?,
            var tabIndex: Int = 0
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
    override fun RibbonController.State.init(props: RibbonController.Props) {
        name = null
        type = null
        tabIndex = 0
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.insertionManager.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionManager.unSubscribe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
        setState {
            name = NameConventions.randomAnonymous()
            type = action
        }
    }


    override fun onUnselected() {
        setState {
            type = null
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

//                paddingTop = 1.em
                paddingRight = 1.75.em
                paddingBottom = 1.em
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


//            styledSpan {
//                css {
//                    marginLeft = 1.em
//                }
//
//                // ...
//            }

            child(MaterialTabs::class) {
                attrs {
                    textColor = "primary"
                    indicatorColor = "primary"

                    value = state.tabIndex

                    onChange = { _, index: Int ->
                        onTab(index)
                    }
                }

                for (ribbonGroup in props.ribbonGroups) {
                    child(MaterialTab::class) {
                        attrs {
                            label = ribbonGroup.title()
                        }
                    }
                }

                child(MaterialTab::class) {
                    attrs {
                        label = "File"
                    }
                }
                child(MaterialTab::class) {
                    attrs {
                        label = "Script"
                    }
                }
                child(MaterialTab::class) {
                    attrs {
                        label = "Data"
                    }
                }
            }

            br {}

            renderSubActions()
        }
    }


    private fun RBuilder.renderSubActions() {
        for (actionType in props.actionTypes) {
            child(MaterialButton::class) {
                attrs {
                    key = actionType.toReference().asString()
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        if (state.type == actionType) {
                            onUnSelect()
                        }
                        else {
                            onSelect(actionType)
                        }
                    }

                    style = reactStyle {
                        if (state.type == actionType) {
                            backgroundColor = Color.blue.lighten(50).withAlpha(0.5)
                        }
                    }
                }

                val title = props.notation
                        .transitiveAttribute(actionType, NameEditor.titleAttribute)
                        ?.asString()
                        ?: actionType.objectPath.name.value

                val icon = props.notation
                        .transitiveAttribute(actionType, ActionController.iconAttribute)
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
                    height = 52.px
                }

                attrs {
                    title = "Kzen (home)"
                }
            }
        }
    }
}
