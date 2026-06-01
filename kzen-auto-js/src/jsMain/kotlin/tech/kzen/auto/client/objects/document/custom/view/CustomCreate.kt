package tech.kzen.auto.client.objects.document.custom.view

import emotion.react.css
import mui.material.*
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.custom.CustomTheme
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomCreateProps: Props {
    var customState: CustomState
    var viewStore: CustomViewStore
    var prototypes: List<ObjectLocation>
}


external interface CustomCreateState: State {
    var expanded: Boolean
    var selectedPrototype: ObjectLocation?
    var dispatching: Boolean
    var lastError: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomCreate(
    props: CustomCreateProps
):
    RPureComponent<CustomCreateProps, CustomCreateState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomCreateState.init(props: CustomCreateProps) {
        expanded = false
        selectedPrototype = null
        dispatching = false
        lastError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddClick() {
        setState {
            expanded = true
            selectedPrototype = props.prototypes.firstOrNull()
            lastError = null
        }
    }


    private fun onCancel() {
        setState {
            expanded = false
            selectedPrototype = null
            lastError = null
        }
    }


    private fun onPrototypeChange(reference: String) {
        val match = props.prototypes.firstOrNull { it.toReference().asString() == reference }
        setState {
            selectedPrototype = match
        }
    }


    private fun onCreate() {
        val prototype = state.selectedPrototype
            ?: return

        if (state.dispatching) {
            return
        }

        setState {
            dispatching = true
            lastError = null
        }

        props.viewStore.createObject(prototype) { error ->
            if (error == null) {
                setState {
                    expanded = false
                    selectedPrototype = null
                    dispatching = false
                }
            }
            else {
                setState {
                    dispatching = false
                    lastError = error
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.expanded) {
            renderForm()
        }
        else {
            renderAddButton()
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            Button {
                variant = ButtonVariant.contained
                size = Size.small
                onClick = { onAddClick() }

                icon("material-symbols:add-circle-outline") {}
                span {
                    css {
                        marginLeft = 0.3.em
                    }
                    +"Add"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderForm() {
        Paper {
            sx {
                backgroundColor = Color("rgb(240, 247, 255)")
                borderLeftStyle = LineStyle.solid
                borderLeftWidth = 3.px
                borderLeftColor = CustomTheme.primary
            }

            CardContent {
                div {
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        fontWeight = FontWeight.bold
                        fontSize = 1.1.em
                        marginBottom = 0.75.em
                        color = CustomTheme.primary
                    }

                    icon("material-symbols:add-circle-outline") {}
                    span {
                        css {
                            marginLeft = 0.3.em
                        }
                        +"New object"
                    }
                }

                renderTypeSelect()

                renderFooter()

                val lastError = state.lastError
                if (lastError != null) {
                    div {
                        css {
                            marginTop = 0.5.em
                            fontStyle = FontStyle.italic
                            color = CustomTheme.warningText
                        }
                        +lastError
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderTypeSelect() {
        div {
            css {
                marginBottom = 0.75.em
            }

            div {
                css {
                    fontSize = 0.85.em
                    marginBottom = 0.25.em
                }
                +"Type"
            }

            Select {
                sx {
                    minWidth = 200.px
                }
                size = Size.small
                value = state.selectedPrototype?.toReference()?.asString() ?: ""

                onChange = { event, _ ->
                    onPrototypeChange(event.target.asDynamic().value as String)
                }

                for (prototype in props.prototypes) {
                    val reference = prototype.toReference().asString()

                    MenuItem {
                        key = Key(reference)
                        value = reference
                        +prototype.objectPath.name.value
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderFooter() {
        div {
            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                onClick = { onCancel() }
                disabled = state.dispatching
                +"Cancel"
            }

            Button {
                sx {
                    marginLeft = 0.5.em
                }
                variant = ButtonVariant.contained
                size = Size.small
                onClick = { onCreate() }
                disabled = state.selectedPrototype == null || state.dispatching
                +(if (state.dispatching) "Creating..." else "Create")
            }
        }
    }
}
