package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import kotlinx.coroutines.delay
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.createRef
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import react.react
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.SaveIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.*
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepNameEditorProps: react.Props {
    var objectLocation: ObjectLocation
//    var notation: GraphNotation
    var description: String
    var title: String
//    var intentToRun: Boolean

//    var runCallback: () -> Unit
    var editSignal: StepNameEditor.EditSignal
}


external interface StepNameEditorState: react.State {
    var editing: Boolean
    var objectName: String
    var saving: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class StepNameEditor(
    props: StepNameEditorProps
):
    RPureComponent<StepNameEditorProps, StepNameEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun title(graphStructure: GraphStructure, objectLocation: ObjectLocation): String {
            val titleAttributeText = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, AutoConventions.titleAttributePath)
                ?.asString()

            return titleAttributeText
                ?: graphStructure.graphNotation.getString(
                    objectLocation, NotationConventions.isAttributePath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
//    private var inputRef: HTMLInputElement? = null
    private val inputRef = createRef<HTMLInputElement>()


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepNameEditorState.init(props: StepNameEditorProps) {
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


    override fun componentDidUpdate(prevProps: StepNameEditorProps, prevState: StepNameEditorState, snapshot: Any) {
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


//    private fun actionTitle(): String {
//        return props
//                .notation
//                .firstAttribute(
//                        props.objectLocation, AutoConventions.titleAttributePath)
//                ?.asString()
//                ?: props.notation.getString(
//                        props.objectLocation, NotationConventions.isAttributePath)
//    }


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
    private fun handleEnterAndEscape(event: KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onRename, ::onCancel)
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


    // TODO: what does this do and how do I get it working (post-migration)?
//    private fun onInputRef(inputRef: HTMLInputElement?) {
//        val isNew = this.inputRef == null && inputRef != null
//
//        this.inputRef = inputRef
//
//        if (isNew) {
//            async {
//                delay(1)
//                this.inputRef?.focus()
//            }
//        }
//    }


    private fun onEdit() {
        if (! state.editing) {
            setState {
                editing = true
            }
        }

        async {
            delay(1)
            inputRef.current?.focus()
        }
    }


//    private fun onRun() {
//        props.runCallback()
//    }
//
//    private fun onRunEnter() {
//        ClientContext.executionIntentGlobal.set(props.objectLocation)
//    }
//
//    private fun onRunLeave() {
//        ClientContext.executionIntentGlobal.clearIf(props.objectLocation)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                height = StepHeader.headerHeight
                width = 100.pct
            }

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun ChildrenBuilder.renderReader() {
        div {
            css {
                display = Display.inlineBlock
                cursor = Cursor.pointer
                height = StepHeader.headerHeight
                width = 100.pct

                marginTop = 10.px
            }

            title = props.description

//            onMouseOver = {
//                onRunEnter()
//            }
//
//            onMouseOut = {
//                onRunLeave()
//            }
//
//            onClick = {
//                onRun()
//            }

            span {
//                if (props.intentToRun) {
//                    className = ClassName(CssClasses.glowingText)
//                }

                css {
                    width = 100.pct
                    height = StepHeader.headerHeight

                    fontSize = 1.5.em
                    fontWeight = FontWeight.bold
                }

                val objectName =
                        if (state.saving) {
                            ObjectName(state.objectName)
                        }
                        else {
                            props.objectLocation.objectPath.name
                        }

                if (AutoConventions.isAnonymous(objectName)) {
                    +props.title
                }
                else {
                    +objectName.value
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        div {
            css {
                display = Display.inlineBlock

//                width = 100.pct.minus(4.em)
                width = 100.pct.minus(5.em)
                height = StepHeader.headerHeight

//                marginTop = 10.px
                marginTop = 8.px
//                backgroundColor = Color.red
            }

            TextField {
                fullWidth = true
                autoFocus = true
                size = Size.small

                this.inputRef = inputRef
//                inputRef = {
//                    onInputRef()
//                }

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

                onKeyDown = { event ->
                    handleEnterAndEscape(event)
                }
            }
        }

        div {
            css {
                marginTop = (-3).px
                float = Float.right
            }

            IconButton {
                title = "Cancel name edit (keyboard shortcut: Escape)"

                css {
                    marginLeft = (-3).em
                }

                onClick = {
                    onCancel()
                }

                CancelIcon::class.react {}
            }

            IconButton {
                title = "Save name (keyboard shortcut: Enter)"

                css {
                    marginLeft = (-0.5).em
                    marginRight = 0.25.em
                }

                onClick = {
                    onRename()
                }

                disabled = ! isModified()

                SaveIcon::class.react {}
            }
        }
    }
}