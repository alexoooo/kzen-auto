package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface SelectLogicEditorProps: AttributeEditorProps {
    var navigationGlobal: NavigationGlobal
}


//---------------------------------------------------------------------------------------------------------------------
// Picks the Logic document (Script / Flow / Job main, or a Custom document's exported logic) that a RunStep or
// Flow vertex delegates to. Cross-document by construction, so the full location IS the wire form.
@Suppress("unused")
class SelectLogicEditor(
    props: SelectLogicEditorProps
):
    SelectReferenceEditorBase<SelectLogicEditorProps, SelectReferenceEditorState>(props)
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
    // Hydrates synchronously, so the field is populated on first paint rather than after a mount round-trip.
    override fun SelectReferenceEditorState.init(props: SelectLogicEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        selected =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)?.asString()
            }
            else {
                null
            }

        options = selectOptions(graphNotation, graphMetadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun selectOptions(
        graphNotation: GraphNotation,
        graphMetadata: GraphMetadata
    ): Array<SelectOption> {
        return options(graphNotation, graphMetadata)
            .map { location ->
                val option: SelectOption = unsafeJso {
                    value = location.asString()
                    label = location.documentPath.name.value
                }
                option
            }
            .toTypedArray()
    }


    private fun options(graphNotation: GraphNotation, graphMetadata: GraphMetadata): List<ObjectLocation> {
        val candidates = mutableListOf<ObjectLocation>()

        for ((path, notation) in graphNotation.documents.map) {
            if (path == props.objectLocation.documentPath) {
                // TODO: avoid suggesting DAG violation?
                continue
            }

            if (AutoConventions.isLogic(graphNotation, path)) {
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
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        val selectedLocation = state.selected?.let { ObjectLocation.parse(it) }

        if (event is RenamedDocumentRefactorEvent &&
                selectedLocation != null &&
                event.removedUnderOldName.documentPath == selectedLocation.documentPath
        ) {
            setSelected(selectedLocation
                .copy(documentPath = event.createdWithNewName.destination)
                .asString())
            return
        }

        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        setOptions(selectOptions(graphStructure.graphNotation, graphStructure.graphMetadata))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun wireValue(optionKey: String): String {
        return optionKey
    }


    private fun onNavigateToSelected() {
        val selected = state.selected
            ?: return
        props.navigationGlobal.goto(ObjectLocation.parse(selected).documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Overrides the base render because the launch button shares a flex row with the field, and the row owns the
    // sizing that keeps the button from overflowing.
    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

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

                selectField(options)
            }

            IconButton {
                sx {
                    marginLeft = 0.25.em
                }
                title = "Open the selected script"
                disabled = state.selected == null

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
}
