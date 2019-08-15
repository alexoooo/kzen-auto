package tech.kzen.auto.client.objects.document.script.step.attribute

import kotlinx.css.em
import kotlinx.css.fontSize
import react.*
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTree
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.browser.document
import kotlin.js.Json
import kotlin.js.json


@Suppress("unused")
class StepSelectEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, StepSelectEditor.State>(props),
        CommandBus.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var value: ObjectLocation?,
            var renaming: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation
    ):
            AttributeEditorWrapper(objectLocation)
    {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>): ReactElement {
            return input.child(StepSelectEditor::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: AttributeEditorProps) {
//        console.log("ParameterEditor | State.init - ${props.name}")

        @Suppress("MoveVariableDeclarationIntoWhen")
        val attributeNotation =
//                props.attributeNotation
                props.graphStructure.graphNotation.transitiveAttribute(
                        props.objectLocation, props.attributeName)

        if (attributeNotation is ScalarAttributeNotation) {
            val reference = ObjectReference.parse(attributeNotation.value)
            val objectLocation = props.graphStructure.graphNotation.coalesce.locate(props.objectLocation, reference)

            value = objectLocation
        }

        renaming = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: AttributeEditorProps,
            prevState: State,
            snapshot: Any
    ) {
        if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else {
                editAttributeCommandAsync()
            }
        }
    }

    override fun componentDidMount() {
        ClientContext.commandBus.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.commandBus.unsubscribe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {}


    override fun onCommandSuccess(
            command: NotationCommand,
            event: NotationEvent
    ) {
        when (command) {
            is RenameObjectRefactorCommand -> {
                val renamedEvent = event as RenamedObjectRefactorEvent

                if (renamedEvent.renamedObject.objectLocation == state.value) {
                    setState {
                        value = renamedEvent.renamedObject.newLocation()
                        renaming = true
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
        console.log("onValueChange - $value")

        setState {
            this.value = value
        }
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val value = state.value
                ?: return

        val localReference = value.toReference()
                .crop(retainNesting = true, retainPath = false)

        ClientContext.commandBus.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(localReference.asString())))
    }


    private fun predecessors(): List<ObjectLocation> {
        val steps = ControlTree.readSteps(
                props.graphStructure, props.objectLocation.documentPath)

        return steps.predecessors(props.objectLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

        val selectOptions = predecessors()
                .map { ReactSelectOption(it.asString(), it.objectPath.name.value) }
                .toTypedArray()

//        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"

        val selectId = "material-react-select-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +formattedLabel()
        }

        child(ReactSelect::class) {
            attrs {
                id = selectId

                value = selectOptions.find { it.value == state.value?.asString() }
//                value = firstOption

                options = selectOptions
//                options = optionsArray

                onChange = {
//                    console.log("^^^^^ selected: $it")

                    onValueChange(ObjectLocation.parse(it.value))
//                    onTypeChange(it.value)
                }

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, state ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles

                // NB: this was causing clipping when used in ConditionalStepDisplay table,
                //   see: https://react-select.com/advanced#portaling
                menuPortalTarget = document.body!!
            }
        }
    }


    private fun formattedLabel(): String {
        val upperCamelCase = props.attributeName.value.capitalize()

        val results = Regex("[A-Z][a-z]*").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }
}
