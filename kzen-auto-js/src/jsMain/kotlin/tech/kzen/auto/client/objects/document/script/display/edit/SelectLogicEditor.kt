package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
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
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface SelectLogicEditorProps: AttributeEditorProps {
    var navigationGlobal: NavigationGlobal
}


external interface SelectLogicEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var options: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
// TODO: convert to RPureComponent
@Suppress("unused")
class SelectLogicEditor(
    props: SelectLogicEditorProps
):
    RComponent<SelectLogicEditorProps, SelectLogicEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectLogicEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                navigationGlobal = this@Wrapper.navigationGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectLogicEditorState.init(props: SelectLogicEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
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
        prevProps: SelectLogicEditorProps,
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
            props.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
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
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
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


    private fun onNavigateToSelected() {
        val value = state.value
            ?: return
        props.navigationGlobal.goto(value.documentPath)
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val value = state.value
                ?: return

        props.mirroredGraphStore.apply(UpsertAttributeCommand(
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
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.documentPath.name.value
                }
                option
            }
            .toTypedArray()

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            // The select grows; minWidth 0 lets it shrink so the launch button never overflows.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                muiAutocompleteField(
                    label = formattedLabel(),
                    options = selectOptions,
                    selectedOption = selectOptions.find { it.value == state.value?.asString() },
                    onSelect = { onValueChange(ObjectLocation.parse(it.value)) },
                    disableClearable = true)
            }

            IconButton {
                css {
                    marginLeft = 0.25.em
                }
                title = "Open the selected script"
                disabled = state.value == null

                onClick = {
                    onNavigateToSelected()
                }

                icon("material-symbols:open-in-new") {
                    style = unsafeJso {
                        fontSize = 1.25.em
                    }
                }
            }
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
