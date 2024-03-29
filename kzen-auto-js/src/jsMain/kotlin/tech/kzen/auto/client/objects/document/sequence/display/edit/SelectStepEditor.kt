package tech.kzen.auto.client.objects.document.sequence.display.edit

import emotion.react.css
import js.objects.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.sequence.model.SequenceGlobal
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface SelectStepEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var initialized: Boolean
    var predecessors: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectStepEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, SelectStepEditorState>(props),
    LocalGraphStore.Observer,
    ClientStateGlobal.Observer,
    SequenceStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectStepEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectStepEditorState.init(props: AttributeEditorProps) {
        value = null
        renaming = false
        initialized = false
        predecessors = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectStepEditorState,
        snapshot: Any
    ) {
        if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else if (prevState.initialized) {
                editAttributeCommandAsync()
            }
        }
    }


    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        SequenceGlobal.get().observe(this)
        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        SequenceGlobal.get().unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing step deleted or renamed and this objectLocation is stale
            return
        }

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val value =
            (attributeNotation as? ScalarAttributeNotation)?.let {
                val reference = ObjectReference.parse(it.value)
                graphNotation.coalesce
                    .locateOptional(reference, objectReferenceHost)
            }

        setState {
            this.value = value
            initialized = true
        }
    }


    override fun onSequenceState(sequenceState: SequenceState, changes: Set<SequenceStore.ChangeType>) {
        if (SequenceStore.ChangeType.Notation !in changes) {
            return
        }

        val sequenceTree = sequenceState.sequenceTree

        val predecessorObjectPaths = sequenceTree.predecessors(props.objectLocation.objectPath)
        val predecessors = predecessorObjectPaths.map { props.objectLocation.documentPath.toObjectLocation(it) }

        if (state.predecessors != predecessors) {
            setState {
                this.predecessors = predecessors
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        when (event) {
            is RenamedObjectRefactorEvent -> {
                if (event.renamedObject.objectLocation == state.value) {
                    setState {
                        value = event.renamedObject.newObjectLocation()
                        renaming = true
                    }
                }
            }

            else -> {}
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
//        console.log("onValueChange - $value")

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
                .crop(retainPath = false)

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(localReference.asString())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val predecessors = state.predecessors
            ?: return

        val selectOptions: Array<ReactSelectOption> = predecessors
            .map { location ->
                val option: ReactSelectOption = jso {
                    this.value = location.asString()
                    this.label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        val selectedValue = selectOptions.find { it.value == state.value?.asString() }

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +formattedLabel()

            ReactSelect::class.react {
                value = selectedValue
                options = selectOptions

                onChange = {
                    onValueChange(ObjectLocation.parse(it.value))
                }

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, _ ->
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
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
