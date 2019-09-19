package tech.kzen.auto.client.objects.document.script.step.header

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationConventions


class StepNameEditor(
        props: Props
):
        RPureComponent<StepNameEditor.Props, StepNameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var notation: GraphNotation,
            var description: String,
            var intentToRun: Boolean,

            var runCallback: () -> Unit,
            var editSignal: EditSignal
    ): RProps


    class State(
            var editing: Boolean,
            var objectName: String,
            var saving: Boolean
    ): RState


    class EditSignal {
        private var callback: (() -> Unit)? = null

        fun trigger() {
            check(callback != null)
            callback!!.invoke()
        }

        fun attach(callback: () -> Unit) {
            check(this.callback == null)
            this.callback = callback
        }

        fun detach(/*callback: () -> Unit*/) {
//            check(this.callback == callback)
            this.callback = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var inputRef: HTMLInputElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ObjectNameEditor | State.init - ${props.objectName}", Date.now())
        objectName = props.objectLocation.objectPath.name.value

        editing = false
        saving = false
//        readerHover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        this.props.editSignal.attach(::onEdit)
    }


    override fun componentWillUnmount() {
        this.props.editSignal.detach()
    }


    override fun componentDidUpdate(prevProps: Props, prevState: State, snapshot: Any) {
        if (state.saving && ! prevState.saving) {
            saveAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isBlank(): Boolean {
        return state.objectName.isBlank()
    }


    private fun isModified(): Boolean {
        return props.objectLocation.objectPath.name.value != state.objectName
    }


    private fun actionTitle(): String {
        return props
                .notation
                .transitiveAttribute(
                        props.objectLocation, AutoConventions.titleAttributePath)
                ?.asString()
                ?: props.notation.getString(
                        props.objectLocation, NotationConventions.isAttributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun saveAsync() {
        val objectName = ObjectName(state.objectName)

        async {
             // NB: not sure why this is necessary, without it state.saving doesn't show
             delay(1)

             ClientContext.mirroredGraphStore.apply(RenameObjectRefactorCommand(
                    props.objectLocation, objectName))

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

        if (isBlank()) {
            return
        }

        setState {
            editing = false
            saving = true
        }
    }


    private fun onInputRef(inputRef: HTMLInputElement?) {
        val isNew = this.inputRef == null && inputRef != null

        this.inputRef = inputRef

        if (isNew) {
            async {
                delay(1)
                this.inputRef?.focus()
            }
        }
    }


    private fun onEdit() {
        if (! state.editing) {
            setState {
                editing = true
            }
        }

        async {
            delay(1)
            inputRef?.focus()
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
    override fun RBuilder.render() {
        styledDiv {
            css {
                height = StepHeader.headerHeight
                width = 100.pct

//                backgroundColor = Color.blue
            }

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun RBuilder.renderReader() {
        styledDiv {
            css {
                display = Display.inlineBlock

                cursor = Cursor.pointer
                height = StepHeader.headerHeight
                width = 100.pct

                marginTop = 10.px
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
                    height = StepHeader.headerHeight

                    fontSize = LinearDimension("1.5em")
                    fontWeight = FontWeight.bold

                    if (props.intentToRun) {
//                        println("^$%^$%^$% props.intentToRun - ${props.intentToRun}")
                        classes.add(CssClasses.glowingText)
                    }
                }

                val objectName =
                        if (state.saving) {
                            ObjectName(state.objectName)
                        }
                        else {
                            props.objectLocation.objectPath.name
                        }

                if (AutoConventions.isAnonymous(objectName)) {
                    +actionTitle()
                }
                else {
                    +objectName.value
                }
            }
        }
    }


    private fun RBuilder.renderEditor() {
        styledDiv {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus(4.em)
                height = StepHeader.headerHeight

//                marginTop = 10.px
                marginTop = 8.px
            }

            child(MaterialTextField::class) {
                attrs {
//                    label = "Name"
                    fullWidth = true
                    autoFocus = true

                    inputRef = ::onInputRef

                    value =
                            if (AutoConventions.isAnonymous(ObjectName(state.objectName))) {
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
                marginTop = (-3).px
                float = Float.right
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel name edit (keyboard shortcut: Escape)"

                    style = reactStyle {
                        marginLeft = (-3).em
                    }

                    onClick = ::onCancel
                }

                child(CancelIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Save name (keyboard shortcut: Enter)"

                    style = reactStyle {
//                        marginLeft = (-0.5).em
                        marginLeft = (-0.75).em
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