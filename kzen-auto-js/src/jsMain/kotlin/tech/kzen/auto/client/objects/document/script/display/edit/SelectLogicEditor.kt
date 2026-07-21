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
import tech.kzen.auto.common.paradigm.logic.LogicCallGraph
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
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

        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val selectedKey =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)?.asString()
            }
            else {
                null
            }

        selected = selectedKey
        options = selectOptions(graphStructure, selectedKey)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun selectOptions(
        graphStructure: GraphStructure,
        selectedKey: String?
    ): Array<SelectOption> {
        return options(graphStructure, selectedKey)
            .map { location ->
                val option: SelectOption = unsafeJso {
                    value = location.asString()
                    label = location.documentPath.name.value
                }
                option
            }
            .toTypedArray()
    }


    private fun options(
        graphStructure: GraphStructure,
        selectedKey: String?
    ): List<ObjectLocation> {
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        // The editor document's transitive CALLERS are dropped from the suggestions: selecting one would close
        // a call cycle `D -> X -> ... -> D`. SUGGESTION FILTER ONLY - recursion is legal at runtime, matching
        // how self was already suppressed as a suggestion but never rejected; raw YAML remains the escape hatch.
        val callers = LogicCallGraph.transitiveCallers(graphStructure, props.objectLocation.documentPath)

        val candidates = mutableListOf<ObjectLocation>()

        for ((path, notation) in graphNotation.documents.map) {
            if (path == props.objectLocation.documentPath || path in callers) {
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

        // An already-set recursive selection is excluded above but must still RENDER — the field matches its
        // value against the option list, so dropping the option would blank a value the user deliberately set
        // (or wrote in raw YAML). A selection whose document no longer exists stays blank, as before.
        val selectedLocation = selectedKey?.let { ObjectLocation.parse(it) }
        if (selectedLocation != null &&
                selectedLocation !in candidates &&
                selectedLocation.documentPath in graphNotation.documents.map
        ) {
            candidates.add(0, selectedLocation)
        }

        return candidates
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        val selectedLocation = state.selected?.let { ObjectLocation.parse(it) }

        // A rename of the SELECTED document is adopted (the refactor already rewrote the notation, so this
        // never writes) and then falls through: every option is keyed by document path, so the list has to be
        // rebuilt against the new one or the field would match nothing and render blank. The adopted key is
        // carried in a local because setSelected goes through setState - reading state.selected back below
        // would still see the old path.
        val selectedKey =
            if (event is RenamedDocumentRefactorEvent &&
                    selectedLocation != null &&
                    event.removedUnderOldName.documentPath == selectedLocation.documentPath
            ) {
                selectedLocation
                    .copy(documentPath = event.createdWithNewName.destination)
                    .asString()
                    .also { setSelected(it) }
            }
            else {
                state.selected
            }

        // NB: the event's own structure, not clientStateGlobal.current() - it IS the post-command notation,
        // whereas another observer's cached copy may not have caught up with this very rename yet.
        setOptions(selectOptions(graphDefinition.graphStructure, selectedKey))
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
