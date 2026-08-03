package tech.kzen.auto.client.objects.document.common.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
// Picks the Context a step's `binds` / `uses` / `releases` declaration names, from every `is: Context` in the
// graph. Wired via `editor: SelectContextEditor` in the step archetype's metadata, which is also what makes the
// declaration body-editable at all (ScriptStepDisplayDefault.renderBody suppresses a declaration naming no
// editor) — so a typed step whose Context is an archetype constant stays uneditable by leaving it unnamed.
//
// SelectObjectEditor cannot serve this: it offers this document's own objects plus other documents' Custom
// exports, matched against the attribute's `is:` constraint, and a Context is neither — it is discovered
// graph-wide by inheritance, wherever its author put it.
@Suppress("unused")
class SelectContextEditor(
    props: AttributeEditorProps
):
    SelectReferenceEditorBase<AttributeEditorProps, SelectReferenceEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectContextEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Hydrates synchronously, so the field is populated on first paint rather than after a mount round-trip.
    override fun SelectReferenceEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        selected = selectedKey(graphNotation)
        options = contextOptions(graphNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Through the shared reference reader, so both notated shapes work: the blessed scalar (`binds: X`) and the
    // list the archetype declares for `uses`. A reference that resolves to nothing leaves the field EMPTY rather
    // than inventing an option for it — the dangling entry survives in notation and the analysis reports it,
    // which is the surface that can say what is wrong.
    private fun selectedKey(graphNotation: GraphNotation): String? {
        val reference = LogicContextConventions
            .stepContextReferences(
                graphNotation, props.objectLocation, AttributePath.ofName(props.attributeName))
            .firstOrNull()
            ?: return null

        return ContextConventions
            .resolveOrNull(graphNotation, reference, props.objectLocation)
            ?.location
            ?.asString()
    }


    private fun contextOptions(graphNotation: GraphNotation): Array<SelectOption> {
        return ContextConventions
            .allContexts(graphNotation)
            .sortedBy { it.label() }
            .map { descriptor ->
                val option: SelectOption = unsafeJso {
                    value = descriptor.location.asString()
                    label = descriptor.label()
                    detail = optionDetailOf(descriptor)
                    detailTitle = descriptor.type.className.asString()
                }
                option
            }
            .toTypedArray()
    }


    // The value contract and the description are what tell two similarly-named declarations apart; the row
    // shows the type unqualified, with the qualified class on hover.
    private fun optionDetailOf(descriptor: ContextDescriptor): String? {
        val parts = listOfNotNull(
            descriptor.typeLabel().ifEmpty { null },
            descriptor.description.ifEmpty { null })
        return parts.joinToString(" — ").ifEmpty { null }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Renames only. A rename invalidates option KEYS (they are ObjectLocation strings), so the list has to be
    // rebuilt or the field would match nothing and render blank; every other notation change is picked up when
    // the user opens the picker (see [refreshOptions]).
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            // Adopting the new name never writes — the refactor already rewrote this declaration.
            is RenamedDocumentRefactorEvent -> {
                val selectedLocation = selectedLocation()
                if (selectedLocation != null &&
                        event.removedUnderOldName.documentPath == selectedLocation.documentPath
                ) {
                    setSelected(selectedLocation
                        .copy(documentPath = event.createdWithNewName.destination)
                        .asString())
                }
            }

            is RenamedObjectRefactorEvent -> {
                if (event.renamedObject.objectLocation.asString() == state.selected) {
                    setSelected(event.renamedObject.newObjectLocation().asString())
                }
            }

            else -> return
        }

        // NB: the event's own structure, not clientStateGlobal.current() - it IS the post-command notation,
        // whereas another observer's cached copy may not have caught up with this very rename yet.
        setOptions(contextOptions(graphDefinition.graphStructure.graphNotation))
    }


    private fun selectedLocation(): ObjectLocation? {
        return state.selected?.let { ObjectLocation.parse(it) }
    }


    // Recomputing on picker-open rather than per notation event is what keeps this field cheap:
    // ContextConventions.allContexts walks the inheritance chain of EVERY object in the coalesced graph, and a
    // committed change yields a fresh GraphNotation whose chain cache starts cold — so an on-every-event
    // refresh would repeat that graph-wide walk in every mounted editor for each debounced keystroke elsewhere
    // in the document. The closed field only ever displays the selected option, so a Context declared since
    // mount costs nothing until the list is actually shown.
    private fun refreshOptions() {
        val graphNotation = props.clientStateGlobal.current()?.graphStructure()?.graphNotation
            ?: return
        setOptions(contextOptions(graphNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Prefer the document-relative object path (`main.contexts/Greeting`) — terser, and what a hand-written
    // declaration looks like — but only when it actually resolves back to THIS Context from the referring step.
    // The choice is by resolution, never by document equality: a user's declaration is nested under its Contexts
    // document's `main`, so its bare name reaches nothing from another document, and two same-named Contexts
    // would bind to whichever the shorter form happens to reach. Both cases fall back to the fully-qualified
    // reference. Getting this wrong writes a silently dangling declaration rather than an error, since a
    // `by: Nominal` reference that resolves to nothing degrades to a validation message.
    override fun wireValue(optionKey: String): String {
        val location = ObjectLocation.parse(optionKey)
        val qualified = location.toReference()
        val objectPath = qualified.crop(retainPath = false).asString()

        val graphNotation = props.clientStateGlobal.current()?.graphStructure()?.graphNotation
            ?: return qualified.asString()

        val resolved = ContextConventions
            .resolveOrNull(graphNotation, objectPath, props.objectLocation)
            ?.location

        return if (resolved == location) objectPath else qualified.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        selectField(options, onOpen = ::refreshOptions)
    }
}
