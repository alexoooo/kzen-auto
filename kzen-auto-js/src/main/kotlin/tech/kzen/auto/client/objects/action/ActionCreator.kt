package tech.kzen.auto.client.objects.action

import kotlinx.css.Color
import kotlinx.css.em
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.wrap.MaterialButton
import tech.kzen.auto.client.wrap.iconClassForName
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.GraphNotation


@Suppress("unused")
class ActionCreator(
        props: ActionCreator.Props
):
        RComponent<ActionCreator.Props, ActionCreator.State>(props),
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var notation: GraphNotation,
            var path: BundlePath
    ): RProps


    class State(
            var name: ObjectName,
            var type: ObjectLocation?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
//        name = NameConventions.randomDefault()

//        val types = actionTypes()
//        if (types.isEmpty()) {
//            throw IllegalStateException("Must provide at least one action type")
//        }
//        type = types[0]
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
//    private fun onTypeChange(newValue: String) {
//        setState {
//            name = NameConventions.randomDefault()
//            type = newValue
//        }
//    }

    private fun onUnSelect() {
        ClientContext.insertionManager.clearSelection()
    }


    private fun onSelect(actionType: ObjectLocation) {
        ClientContext.insertionManager.setSelected(actionType)
//        async {
//            delay(1)
//
//            ClientContext.commandBus.apply(AddObjectCommand.ofParent(
//                    props.path,
//                    state.name,
//                    state.type))
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        for (actionType in actionTypes()) {
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
    private fun actionTypes(): List<ObjectLocation> {
        val actionTypes = mutableListOf<ObjectLocation>()

        for (e in props.notation.coalesce.values) {
            val isParameter = e.value.attributes[NotationConventions.isAttribute.attribute]
                    ?.asString()
                    ?: continue

            if (isParameter != "Action") {
                continue
            }

            actionTypes.add(e.key)
        }

        return actionTypes
    }
}
