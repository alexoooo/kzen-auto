package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.select.reactSelectField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SelectLogicEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var options: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectLogicEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, SelectLogicEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectLogicEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectLogicEditorState.init(props: AttributeEditorProps) {
        val graphStructure = ClientContext.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        value =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                val objectLocation = graphNotation.coalesce.locateOptional(reference, objectReferenceHost)
                objectLocation
            }
            else {
                null
            }

        renaming = false
        options = options(graphNotation, graphMetadata)
    }


    private fun options(graphNotation: GraphNotation, graphMetadata: GraphMetadata): List<ObjectLocation> {
        val candidates = mutableListOf<ObjectLocation>()

        for ((path, notation) in graphNotation.documents.map) {
            if (path == props.objectLocation.documentPath) {
                // TODO: avoid suggesting DAG violation?
                continue
            }

            if (AutoConventions.isLogic(notation)) {
                candidates.add(ObjectLocation(
                        path, NotationConventions.mainObjectPath))
                continue
            }

            if (CustomConventions.isCustomDocument(notation)) {
                candidates.addAll(CustomConventions.customDocumentExportedLogic(
                    graphNotation, graphMetadata, path, notation))
            }
        }

        return candidates
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectLogicEditorState,
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
        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                if (event.removedUnderOldName.documentPath == state.value?.documentPath) {
                    val newLocation =
                        state.value!!.copy(documentPath = event.createdWithNewName.destination)

                    setState {
                        value = newLocation
                        renaming = true
                    }
                }
                else {
                    updateOptions()
                }
            }

            else -> {
                updateOptions()
            }
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    private fun updateOptions() {
        val graphStructure = ClientContext.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata
        setState {
            options = options(graphNotation, graphMetadata)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
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

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(value.asString())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        val selectOptions = options
            .map {
                val option: ReactSelectOption = unsafeJso {
                    value = it.asString()
                    label = it.documentPath.name.value
                }
                option
            }
            .toTypedArray()

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +formattedLabel()

            reactSelectField(
                selectedOption = selectOptions.find { it.value == state.value?.asString() },
                options = selectOptions,
                onSelect = { onValueChange(ObjectLocation.parse(it.value)) })
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
