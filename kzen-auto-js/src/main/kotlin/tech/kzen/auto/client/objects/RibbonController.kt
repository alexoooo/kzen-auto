package tech.kzen.auto.client.objects

import kotlinx.css.Color
import kotlinx.css.em
import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.action.NameEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.wrap.MaterialButton
import tech.kzen.auto.client.wrap.iconClassForName
import tech.kzen.auto.client.wrap.reactStyle
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
//            var actionTypes: ActionManager,
            var actionTypes: List<ObjectLocation>,

            var notation: GraphNotation
    ): RProps


    class State(
            var name: ObjectName,
            var type: ObjectLocation?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val actionTypes: List<ObjectLocation>
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(RibbonController::class) {
                attrs {
                    actionTypes = this@Wrapper.actionTypes
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.insertionManager.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionManager.unSubscribe(this)
    }


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


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
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


    //-----------------------------------------------------------------------------------------------------------------
//    private fun actionTypes(): List<ObjectLocation> {
//        val actionTypes = mutableListOf<ObjectLocation>()
//
//        for (e in props.notation.coalesce.values) {
//            val isParameter = e.value.attributes[NotationConventions.isAttribute.attribute]
//                    ?.asString()
//                    ?: continue
//
//            if (isParameter != "Action") {
//                continue
//            }
//
//            actionTypes.add(e.key)
//        }
//
//        return actionTypes
//    }
}
