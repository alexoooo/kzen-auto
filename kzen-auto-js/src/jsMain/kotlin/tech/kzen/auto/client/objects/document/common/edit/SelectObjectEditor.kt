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
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SelectObjectEditorState: SelectReferenceEditorState {
    // Tri-state on purpose: null = not resolved yet, so an unset slot never reads as "constraint is missing".
    var constraintMissing: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
// Picks any object satisfying the attribute's `is:` type constraint, from this document plus other documents'
// exports. Same-document picks are written as a bare name; cross-document picks keep the full reference.
@Suppress("unused")
class SelectObjectEditor(
    props: AttributeEditorProps
):
    SelectReferenceEditorBase<AttributeEditorProps, SelectObjectEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val isAttributeNesting = NotationConventions.isAttributePath.toNesting()
    }


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
            SelectObjectEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Hydrates synchronously, so the field is populated on first paint rather than after a mount round-trip.
    override fun SelectObjectEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val attributeNotation = graphNotation.firstAttribute(
            props.objectLocation, AttributePath.ofName(props.attributeName))

        selected =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)?.asString()
            }
            else {
                null
            }

        val resolvedConstraint = constraintLocation(graphNotation, graphMetadata)
        constraintMissing = resolvedConstraint == null
        options = resolvedConstraint?.let { selectOptions(graphNotation, it) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun constraintLocation(
        graphNotation: GraphNotation,
        graphMetadata: GraphMetadata
    ): ObjectLocation? {
        val isValue = graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(isAttributeNesting)
            ?.asString()
            ?: return null

        val reference = ObjectReference.parse(isValue)
        return graphNotation.coalesce.locateOptional(
            reference, ObjectReferenceHost.ofLocation(props.objectLocation))
    }


    private fun selectOptions(graphNotation: GraphNotation, constraint: ObjectLocation): Array<SelectOption> {
        return options(graphNotation, constraint)
            .map { location ->
                val option: SelectOption = unsafeJso {
                    value = location.asString()
                    label = optionLabel(location)
                }
                option
            }
            .toTypedArray()
    }


    private fun options(graphNotation: GraphNotation, constraint: ObjectLocation): List<ObjectLocation> {
        val editorLocation = props.objectLocation
        val raw = mutableSetOf<ObjectLocation>()

        val localDocument = graphNotation.documents.map[editorLocation.documentPath]
        if (localDocument != null) {
            for (objectPath in localDocument.objects.notations.map.keys) {
                val candidate = editorLocation.documentPath.toObjectLocation(objectPath)
                if (matchesConstraint(graphNotation, candidate, constraint)) {
                    raw.add(candidate)
                }
            }
        }

        for ((path, notation) in graphNotation.documents.map) {
            if (path == editorLocation.documentPath) {
                continue
            }

            if (CustomConventions.isCustomDocument(notation)) {
                for (export in CustomConventions.customDocumentExports(graphNotation, path, notation)) {
                    if (matchesConstraint(graphNotation, export, constraint)) {
                        raw.add(export)
                    }
                }
            }

            val mainLocation = path.toMainObjectLocation()
            if (mainLocation in graphNotation.coalesce &&
                    matchesConstraint(graphNotation, mainLocation, constraint)) {
                raw.add(mainLocation)
            }
        }

        val referencedBy = buildReferencedByMap(graphNotation)
        val ancestors = computeAncestors(editorLocation, referencedBy)

        return raw
            .asSequence()
            .filter { it != editorLocation && it !in ancestors }
            .sortedBy { it.asString() }
            .toList()
    }


    private fun matchesConstraint(
        graphNotation: GraphNotation,
        candidate: ObjectLocation,
        constraint: ObjectLocation
    ): Boolean {
        if (isAbstract(graphNotation, candidate)) {
            return false
        }
        return constraint in graphNotation.inheritanceChain(candidate)
    }


    private fun isAbstract(graphNotation: GraphNotation, candidate: ObjectLocation): Boolean {
        return graphNotation
            .directAttribute(candidate, NotationConventions.abstractAttributePath)
            ?.asBoolean() == true
    }


    //-----------------------------------------------------------------------------------------------------------------
    // DAG preservation: scan every (object, attribute) for resolvable references and build a reverse-edge map.
    // The `is:` attribute is skipped — that edge is inheritance, not data-flow, and would mark every subtype as
    // an ancestor of its supertype.
    private fun buildReferencedByMap(
        graphNotation: GraphNotation
    ): Map<ObjectLocation, Set<ObjectLocation>> {
        val edges = mutableMapOf<ObjectLocation, MutableSet<ObjectLocation>>()
        for ((location, notation) in graphNotation.coalesce.map) {
            val host = ObjectReferenceHost.ofLocation(location)
            for ((attrName, attrNotation) in notation.attributes.map) {
                if (attrName == NotationConventions.isAttributeName) {
                    continue
                }
                collectReferences(attrNotation, graphNotation, host) { resolved ->
                    edges.getOrPut(resolved) { mutableSetOf() }.add(location)
                }
            }
        }
        return edges
    }


    private fun collectReferences(
        notation: AttributeNotation,
        graphNotation: GraphNotation,
        host: ObjectReferenceHost,
        onResolved: (ObjectLocation) -> Unit
    ) {
        when (notation) {
            is ScalarAttributeNotation -> {
                val reference = ObjectReference.tryParse(notation.value)
                    ?: return
                val resolved = graphNotation.coalesce.locateOptional(reference, host)
                if (resolved != null) {
                    onResolved(resolved)
                }
            }

            is ListAttributeNotation ->
                notation.values.forEach {
                    collectReferences(it, graphNotation, host, onResolved)
                }

            is MapAttributeNotation ->
                notation.map.values.forEach {
                    collectReferences(it, graphNotation, host, onResolved)
                }
        }
    }


    private fun computeAncestors(
        editorLocation: ObjectLocation,
        referencedBy: Map<ObjectLocation, Set<ObjectLocation>>
    ): Set<ObjectLocation> {
        val ancestors = mutableSetOf<ObjectLocation>()
        val queue = ArrayDeque<ObjectLocation>()
        queue.add(editorLocation)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val referrers = referencedBy[current]
                ?: continue
            for (referrer in referrers) {
                if (ancestors.add(referrer)) {
                    queue.add(referrer)
                }
            }
        }
        return ancestors
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        // A rename of the SELECTED object / document is adopted (the refactor already rewrote the notation, so
        // this never writes) and then falls through to the refresh: every option is keyed by its location, so
        // the list has to be rebuilt against the new one or the field would match nothing and render blank.
        when (event) {
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

            else -> {}
        }

        // NB: the event's own structure, not clientStateGlobal.current() - it IS the post-command notation,
        // whereas another observer's cached copy may not have caught up with this very rename yet.
        refresh(graphDefinition.graphStructure)
    }


    private fun selectedLocation(): ObjectLocation? {
        return state.selected?.let { ObjectLocation.parse(it) }
    }


    private fun refresh(graphStructure: GraphStructure) {
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val resolvedConstraint = constraintLocation(graphNotation, graphMetadata)

        val constraintMissing = resolvedConstraint == null
        if (state.constraintMissing != constraintMissing) {
            setState {
                this.constraintMissing = constraintMissing
            }
        }

        if (resolvedConstraint != null) {
            setOptions(selectOptions(graphNotation, resolvedConstraint))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun wireValue(optionKey: String): String {
        val location = ObjectLocation.parse(optionKey)

        val reference =
            if (location.documentPath == props.objectLocation.documentPath) {
                location.toReference().crop(retainPath = false)
            }
            else {
                location.toReference()
            }

        return reference.asString()
    }


    private fun optionLabel(location: ObjectLocation): String {
        return if (location.documentPath == props.objectLocation.documentPath) {
            location.objectPath.asString()
        }
        else {
            "${location.documentPath.name.value} / ${location.objectPath.asString()}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Overrides the base render because the missing-constraint case REPLACES the field rather than decorating it.
    override fun ChildrenBuilder.render() {
        if (state.constraintMissing == true) {
            +"[SelectObjectEditor: missing `is:` type constraint in attribute metadata]"
            return
        }

        val options = state.options
            ?: return

        selectField(options)
    }
}
