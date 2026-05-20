package tech.kzen.auto.client.objects.document.common.edit

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface SelectObjectEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var constraint: ObjectLocation?
    var options: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectObjectEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, SelectObjectEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val isAttributeNesting = NotationConventions.isAttributePath.toNesting()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectObjectEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectObjectEditorState.init(props: AttributeEditorProps) {
        val graphStructure = ClientContext.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val attributeNotation = graphNotation.firstAttribute(
            props.objectLocation, AttributePath.ofName(props.attributeName))

        value =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)
            }
            else {
                null
            }

        renaming = false

        val resolvedConstraint = constraintLocation(graphNotation, graphMetadata)
        constraint = resolvedConstraint
        options = resolvedConstraint?.let { options(graphNotation, it) }
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
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectObjectEditorState,
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
                    val newLocation = state.value!!
                        .copy(documentPath = event.createdWithNewName.destination)
                    setState {
                        value = newLocation
                        renaming = true
                    }
                }
                else {
                    refresh()
                }
            }

            is RenamedObjectRefactorEvent -> {
                if (event.renamedObject.objectLocation == state.value) {
                    setState {
                        value = event.renamedObject.newObjectLocation()
                        renaming = true
                    }
                }
                else {
                    refresh()
                }
            }

            else -> refresh()
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    private fun refresh() {
        val graphStructure = ClientContext.clientStateGlobal.current()!!.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val graphMetadata = graphStructure.graphMetadata

        val resolvedConstraint = constraintLocation(graphNotation, graphMetadata)
        val nextOptions = resolvedConstraint?.let { options(graphNotation, it) }

        if (state.constraint != resolvedConstraint || state.options != nextOptions) {
            setState {
                constraint = resolvedConstraint
                options = nextOptions
            }
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

        val reference =
            if (value.documentPath == props.objectLocation.documentPath) {
                value.toReference().crop(retainPath = false)
            }
            else {
                value.toReference()
            }

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
            props.objectLocation,
            props.attributeName,
            ScalarAttributeNotation(reference.asString())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.constraint == null) {
            +"[SelectObjectEditor: missing `is:` type constraint in attribute metadata]"
            return
        }

        val options = state.options
            ?: return

        val selectOptions: Array<ReactSelectOption> = options
            .map { location ->
                val option: ReactSelectOption = unsafeJso {
                    this.value = location.asString()
                    this.label = optionLabel(location)
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
                this.options = selectOptions

                onChange = {
                    onValueChange(ObjectLocation.parse(it.value))
                }

                val styleTransformer: (Json, Json) -> Json = { base, _ ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles

                menuPortalTarget = document.body!!
            }
        }
    }


    private fun optionLabel(location: ObjectLocation): String {
        return if (location.documentPath == props.objectLocation.documentPath) {
            location.objectPath.asString()
        }
        else {
            "${location.documentPath.name.value} / ${location.objectPath.asString()}"
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
