package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.objects.document.common.dragdrop.ObjectTreeReorder
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.FontWeight
import web.cssom.LineStyle
import web.cssom.em
import web.cssom.number
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface ScopeBodyEditorProps: AttributeEditorProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface ScopeBodyEditorState: State {
    var items: List<ScopeBodyEditor.Item>?
    var archetypes: List<ScopeBodyEditor.Archetype>?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Editor for a `by: NestedList` attribute whose items are nested objects, each an instance of an archetype of the
 * attribute's declared `of:` type (an `EntryScope`'s `body` of `ScopeBodyWorker`s, content streaming spike). The
 * items are the objects nested under `<owner>.<attribute>/`, in document order (the order the scope chains them),
 * so every edit is a document-structure command: add is an [AddObjectCommand] of the picked archetype placed after
 * the last item (or right after the owner when there is none), remove is a [RemoveObjectCommand] (an item is a
 * leaf), and move is a [ShiftObjectTreeCommand] through [ObjectTreeReorder]. Each item's attributes render through
 * the generic [AttributeEditorManager] pointed at the item, so an archetype's `editor:` metadata (a select, the
 * shared-format picker) applies unchanged, and a new archetype needs no editor work here.
 *
 * Mounted by the Entry Scope card display rather than registered as an `editor:`, because an AttributeEditor
 * holding the editor manager would close a reference cycle through the manager's autowired list.
 */
@Suppress("unused")
class ScopeBodyEditor(
    props: ScopeBodyEditorProps
):
    ObjectScopedComponent<ScopeBodyEditorProps, ScopeBodyEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val ofAttributeNesting = AttributePath.ofName(AttributeName("of")).toNesting()
        private const val selfDefinerName = "Self"
        private const val nameSeparator = " "
        private val itemBorder = Color("#d8d8d8")
    }


    /** One body item: the nested object and the attributes its card edits, in metadata order. */
    data class Item(
        val location: ObjectLocation,
        val title: String,
        val attributes: List<AttributeName>
    )


    /** An archetype the attribute's `of:` type admits, offered by title. */
    data class Archetype(
        val location: ObjectLocation,
        val title: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScopeBodyEditorState.init(props: ScopeBodyEditorProps) {
        // Hydrated synchronously so the first render already lists the items (no mount-time flicker or write).
        val clientState = props.clientStateGlobal.current()
        items = clientState?.let { readItems(it) }
        archetypes = clientState?.let { readArchetypes(it) }
    }


    override fun onClientState(clientState: ClientState) {
        val items = readItems(clientState)
        val archetypes = readArchetypes(clientState)

        // Structural compare: a progress tick republishes the same notation, and must not re-render every item.
        if (state.items == items && state.archetypes == archetypes) {
            return
        }

        setState {
            this.items = items
            this.archetypes = archetypes
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readItems(clientState: ClientState): List<Item>? {
        val graphStructure = clientState.graphStructure()
        val graphNotation = graphStructure.graphNotation
        val documentNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?: return null

        return documentNotation
            .directNestedObjectPaths(props.objectLocation.objectPath, props.attributeName)
            .map { objectPath ->
                val location = ObjectLocation(props.objectLocation.documentPath, objectPath)
                val attributes = graphStructure.graphMetadata.objectMetadata[location]
                    ?.attributes?.map.orEmpty()
                    .filter { (attributeName, attributeMetadata) ->
                        !AutoConventions.isManaged(attributeName) &&
                                attributeMetadata.definerReference?.name?.objectName?.value != selfDefinerName
                    }
                    .map { it.key }
                Item(location, title(graphNotation, location), attributes)
            }
    }


    /** The archetypes of the `of:` type: the abstract objects below it in the inheritance graph, by title. */
    private fun readArchetypes(clientState: ClientState): List<Archetype>? {
        val graphStructure = clientState.graphStructure()
        val graphNotation = graphStructure.graphNotation

        val ofValue = graphStructure.graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(ofAttributeNesting)
            ?.asString()
            ?: return null

        val base = graphNotation.coalesce.locateOptional(
            ObjectReference.parse(ofValue), ObjectReferenceHost.ofLocation(props.objectLocation))
            ?: return null

        return graphNotation.coalesce.map.keys
            .filter { candidate ->
                candidate != base &&
                        base in graphNotation.inheritanceChain(candidate) &&
                        graphNotation.directAttribute(candidate, NotationConventions.abstractAttributePath)
                            ?.asBoolean() == true
            }
            .map { Archetype(it, title(graphNotation, it)) }
            .sortedBy { it.title }
    }


    private fun title(graphNotation: GraphNotation, location: ObjectLocation): String {
        return graphNotation.firstAttribute(location, AutoConventions.titleAttributePath)?.asString()
            ?: location.objectPath.name.value
    }


    private fun documentNotation(): DocumentNotation? {
        return props.clientStateGlobal.current()
            ?.graphStructure()?.graphNotation?.documents?.get(props.objectLocation.documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd(archetype: Archetype) {
        val documentNotation = documentNotation()
            ?: return

        val existingNames = documentNotation.objects.notations.map.keys.map { it.name }.toSet()
        val newName = NextAvailableName
            .find(archetype.title, separator = nameSeparator) { ObjectName(it) !in existingNames }
            ?.let { ObjectName(it) }
            ?: AutoConventions.randomAnonymous()

        val location = ObjectLocation(
            props.objectLocation.documentPath,
            props.objectLocation.objectPath.nest(AttributePath.ofName(props.attributeName), newName))

        // After the last item, or right after the owner when there is none, so the branch reads in order on disk.
        val existing = documentNotation.directNestedObjectPaths(props.objectLocation.objectPath, props.attributeName)
        val anchor = existing.lastOrNull() ?: props.objectLocation.objectPath
        val insertionIndex = documentNotation.indexOf(anchor).value + 1

        async {
            props.mirroredGraphStore.apply(AddObjectCommand.ofParent(
                location, PositionRelation.at(insertionIndex), archetype.location.objectPath.name))
        }
    }


    private fun onRemove(item: Item) {
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(item.location))
        }
    }


    /** Moves the item at [index] into the gap at [insertionIndex] (0..size, counted before the item leaves). */
    private fun onMove(index: Int, insertionIndex: Int) {
        val items = state.items ?: return
        val documentNotation = documentNotation() ?: return

        val position = ObjectTreeReorder.reorderPosition(
            documentNotation.objects.notations.map.keys.toList(),
            items.map { it.location.objectPath },
            index,
            insertionIndex)
            ?: return

        async {
            props.mirroredGraphStore.apply(ShiftObjectTreeCommand(items[index].location, position))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val items = state.items ?: return

        div {
            css {
                marginBottom = 0.5.em
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +"Body"
            }

            if (items.isEmpty()) {
                div {
                    css {
                        color = Color("#757575")
                    }
                    +"No body steps: each entry passes through unchanged"
                }
            }

            for ((index, item) in items.withIndex()) {
                renderItem(item, index, items.size)
            }

            renderAdd()
        }
    }


    private fun ChildrenBuilder.renderItem(item: Item, index: Int, count: Int) {
        div {
            key = Key(item.location.asString())

            css {
                borderWidth = 1.px
                borderStyle = LineStyle.solid
                borderColor = itemBorder
                borderRadius = 4.px
                padding = 0.5.em
                marginTop = 0.5.em
            }

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginBottom = 0.25.em
                }

                span {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +item.location.objectPath.name.value
                }

                if (item.location.objectPath.name.value != item.title) {
                    span {
                        css {
                            marginLeft = 0.5.em
                            color = Color("#757575")
                        }
                        +item.title
                    }
                }

                div {
                    css {
                        flexGrow = number(1.0)
                    }
                }

                IconButton {
                    title = "Move up"
                    size = Size.small
                    disabled = index == 0
                    onClick = { onMove(index, index - 1) }
                    icon("material-symbols:arrow-upward") {}
                }

                IconButton {
                    title = "Move down"
                    size = Size.small
                    disabled = index == count - 1
                    onClick = { onMove(index, index + 2) }
                    icon("material-symbols:arrow-downward") {}
                }

                IconButton {
                    title = "Remove"
                    size = Size.small
                    onClick = { onRemove(item) }
                    icon("material-symbols:delete-outline") {}
                }
            }

            for (attributeName in item.attributes) {
                div {
                    css {
                        marginBottom = 0.5.em
                    }
                    props.attributeEditorManager.child(this) {
                        this.objectLocation = item.location
                        this.attributeName = attributeName
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderAdd() {
        val archetypes = state.archetypes.orEmpty()
        val options = archetypes
            .map { archetype ->
                val option: SelectOption = unsafeJso {
                    value = archetype.location.asString()
                    label = archetype.title
                }
                option
            }
            .toTypedArray()

        div {
            css {
                marginTop = 0.5.em
            }

            AddNameForm::class.react {
                entityLabel = "body step"
                fieldLabel = "Body step"
                isDuplicate = { false }
                onAdd = { value ->
                    archetypes.firstOrNull { it.location.asString() == value }?.let { onAdd(it) }
                }
                this.options = options
            }
        }
    }
}
