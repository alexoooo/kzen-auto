package tech.kzen.auto.client.objects.action

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RenameRefactorCommand
import tech.kzen.lib.common.structure.notation.model.GraphNotation


class NameEditor(
        props: NameEditor.Props
):
        RComponent<NameEditor.Props, NameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val titleAttribute = AttributePath.ofAttribute(AttributeName("title"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var notation: GraphNotation,
            var description: String,
            var intentToRun: Boolean,
            var runCallback: () -> Unit
    ): RProps


    class State(
            var editing: Boolean,
            var objectName: String,
            var saving: Boolean,
            var readerHover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun NameEditor.State.init(props: NameEditor.Props) {
//        console.log("NameEditor | State.init - ${props.objectName}", Date.now())
        objectName = props.objectLocation.objectPath.name.value

        editing = false
        saving = false
        readerHover = false
    }



    override fun componentDidUpdate(prevProps: Props, prevState: State, snapshot: Any) {
        if (state.saving && ! prevState.saving) {
            saveAsync()
        }
    }


    private fun saveAsync() {
        val adjustedName =
                if (state.objectName.isBlank()) {
                    NameConventions.randomAnonymous()
                }
                else {
                    ObjectName(state.objectName)
                }

        if (state.objectName != adjustedName.value) {
            console.log("$$$$$$ saveAsync - '${state.objectName}' != '$adjustedName'")
            setState {
                objectName = adjustedName.value
            }
        }

        async {
             // NB: not sure why this is necessary, without it state.saving doesn't show
             delay(1)

             ClientContext.commandBus.apply(RenameRefactorCommand(
                    props.objectLocation, adjustedName))

             // NB: no need to set saving = false, the component will un-mount
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handleEnterAndEscape(event: KeyboardEvent) {
//        console.log("event.key: ${event.key}", event)

        when {
            event.key == "Enter" -> onRename()
            event.key == "Escape" -> onCancel()
            else -> return
        }

        event.preventDefault()
    }


    private fun onNameChange(newValue: String) {
        setState {
            objectName = newValue
        }
    }


    private fun onCancel() {
        setState {
            editing = false
        }
    }


    private fun onRename() {
        if (! isModified()) {
            onCancel()
            return
        }

        setState {
            editing = false
            saving = true
        }
    }


    private fun onReaderEnter() {
        setState {
            readerHover = true
        }
    }


    private fun onReaderLeave() {
        setState {
            readerHover = false
        }
    }


    private fun onEdit() {
        setState {
            editing = true
        }
    }


    private fun onRun() {
        props.runCallback()
    }

    private fun onRunEnter() {
        ClientContext.executionIntent.set(props.objectLocation)
    }

    private fun onRunLeave() {
        ClientContext.executionIntent.clearIf(props.objectLocation)
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun isModified(): Boolean {
        return props.objectLocation.objectPath.name.value != state.objectName
    }


    private fun title(): String {
        val type = props.notation.getString(
                props.objectLocation, NotationConventions.isPath)

        return props
                .notation
                .transitiveAttribute(props.objectLocation, titleAttribute)
                ?.asString()
                ?: type
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                height = ActionController.headerHeight
                width = 100.pct

//                backgroundColor = Color.blue
            }

            if (state.editing) {
                renderEditor()
            }
            else {
                attrs {
                    onMouseOverFunction = {
                        onReaderEnter()
                    }

                    onMouseOutFunction = {
                        onReaderLeave()
                    }
                }

                renderReader()
            }
        }
    }


    private fun RBuilder.renderReader() {
        styledDiv {
            css {
                display = Display.inlineBlock

                cursor = Cursor.pointer
                height = ActionController.headerHeight
                width = 100.pct.minus(2.em)

                marginTop = 10.px
//                backgroundColor = Color.red
            }

            attrs {
                title = props.description

                onMouseOverFunction = {
                    onRunEnter()
                }

                onMouseOutFunction = {
                    onRunLeave()
                }

                onClickFunction = {
                    onRun()
                }
            }

            styledSpan {
                css {
                    width = 100.pct
                    height = ActionController.headerHeight

                    fontSize = LinearDimension("1.5em")
                    fontWeight = FontWeight.bold

                    if (props.intentToRun) {
//                        println("^$%^$%^$% props.intentToRun - ${props.intentToRun}")
                        classes.add("glowingText")
                    }
                }

                val objectName =
                        if (state.saving) {
                            ObjectName(state.objectName)
                        }
                        else {
                            props.objectLocation.objectPath.name
                        }

                if (NameConventions.isDefault(objectName)) {
                    +title()
                }
                else {
                    +objectName.value
                }
            }
        }


        styledDiv {
            css {
                float = Float.right

                if (! state.readerHover) {
                    visibility = Visibility.hidden
                }
            }

            attrs {
                title = "Edit name"
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-0.5).em
                        marginRight = (-0.5).em
                    }

                    onClick = ::onEdit
                }

                child(EditIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderEditor() {
        styledDiv {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus(4.em)
                height = ActionController.headerHeight

                marginTop = 10.px
            }

            child(MaterialTextField::class) {
                attrs {
//                    label = "Name"
                    fullWidth = true
                    autoFocus = true

                    value =
                            if (NameConventions.isDefault(ObjectName(state.objectName))) {
                                ""
                            }
                            else {
                                state.objectName
                            }

                    onChange = {
                        val target = it.target as HTMLInputElement
                        onNameChange(target.value)
                    }

                    onKeyDown = ::handleEnterAndEscape
                }
            }
        }

        styledDiv {
            css {
                float = Float.right
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-3).em
                    }

                    onClick = ::onCancel
                }

                child(CancelIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-0.5).em
                        marginRight = (-1).em
                    }

                    onClick = ::onRename

                    disabled = ! isModified()
                }

                child(SaveIcon::class) {}
            }
        }
    }
}