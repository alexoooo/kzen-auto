package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.CardContent
import mui.material.MenuItem
import mui.material.Paper
import mui.material.Select
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import web.cssom.Color
import web.cssom.FontStyle
import web.cssom.FontWeight
import web.cssom.NamedColor
import web.cssom.em
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface CustomCreateProps: Props {
    var documentPath: DocumentPath
    var documentNotation: DocumentObjectNotation
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

        val newName = nextAvailableName(prototype.objectPath.name)
        val newPath = NotationConventions.mainObjectPath.nest(
            CustomConventions.objectsAttributePath, newName)
        val newLocation = ObjectLocation(props.documentPath, newPath)
        val endOfDocument = PositionRelation.at(props.documentNotation.notations.map.size)
        val command = AddObjectCommand.ofParent(newLocation, endOfDocument, prototype.objectPath.name)

        setState {
            dispatching = true
            lastError = null
        }

        async {
            val result = ClientContext.mirroredGraphStore.apply(command)
            when (result) {
                is MirroredGraphSuccess -> {
                    setState {
                        expanded = false
                        selectedPrototype = null
                        dispatching = false
                    }
                }

                is MirroredGraphError -> {
                    setState {
                        dispatching = false
                        lastError = result.error.message ?: result.error.toString()
                    }
                }
            }
        }
    }


    private fun nextAvailableName(prototypeName: ObjectName): ObjectName {
        val attributePath = CustomConventions.objectsAttributePath
        val taken: Set<String> = props.documentNotation.notations.map.keys
            .filter {
                it.nesting.segments.size == 1 &&
                    it.nesting.segments.first().objectName == ObjectName.main &&
                    it.nesting.segments.first().attributePath == attributePath
            }
            .map { it.name.value }
            .toSet()
        if (prototypeName.value !in taken) {
            return prototypeName
        }
        for (i in 2..1000) {
            val candidate = "${prototypeName.value}$i"
            if (candidate !in taken) {
                return ObjectName(candidate)
            }
        }
        return ObjectName("${prototypeName.value}${props.documentNotation.notations.map.size + 1}")
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
                +"+ Add"
            }
        }
    }


    private fun ChildrenBuilder.renderForm() {
        Paper {
            sx {
                backgroundColor = NamedColor.white
            }

            CardContent {
                div {
                    css {
                        fontWeight = FontWeight.bold
                        fontSize = 1.1.em
                        marginBottom = 0.75.em
                    }
                    +"New object"
                }

                renderTypeSelect()

                renderFooter()

                val lastError = state.lastError
                if (lastError != null) {
                    div {
                        css {
                            marginTop = 0.5.em
                            fontStyle = FontStyle.italic
                            color = Color("rgb(128, 80, 0)")
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
                css {
                    fontSize = 0.9.em
                    minWidth = 200.px
                }
                value = state.selectedPrototype?.toReference()?.asString() ?: ""

                onChange = { event, _ ->
                    val target: dynamic = event.target
                    val value = target.value as String
                    onPrototypeChange(value)
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
                css {
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
