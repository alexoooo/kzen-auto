package tech.kzen.auto.client.objects.document.context

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Key
import react.Props
import react.ReactNode
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.objects.document.common.dragdrop.dropMarkerFor
import tech.kzen.auto.client.objects.document.common.signature.logicTypePicker
import tech.kzen.auto.client.objects.document.objectLocationMarker
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.common.dragdrop.ObjectTreeReorder
import tech.kzen.auto.common.objects.document.logic.TypeMetadataDefiner
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.persistentMapOf
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface ContextsControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface ContextsControllerState: State {
    var documentPath: DocumentPath?
    var declarations: List<ContextsController.DeclarationRow>?

    // The declaration currently expanded into the full inline editor (null = all collapsed to readers).
    var editingLocation: ObjectLocation?

    // Local text of the edited declaration's fields; committed on done so a rename (a refactor, which moves
    // the object) does not fire per keystroke. `type` is the exception — it is a picker, so it live-applies.
    var editingName: String
    var editingQualifier: String
    var editingKey: String
    var editingTitle: String
    var editingIcon: String
    var editingDescription: String

    var adding: Boolean
    var newName: String
    var addError: String?

    // Drag-reorder state. dragIndex = the row being dragged; dropIndex + dropAfter = the hovered gap.
    var dragIndex: Int?
    var dropIndex: Int?
    var dropAfter: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a Contexts document: a user's own `is: Context` declarations, nested under `main.contexts/`.
 *
 * Every declaration is a real notation object with its own [ObjectLocation], so this editor is the same shape
 * as the Script signature's parameter list and reuses the same machinery wholesale — [AddObjectCommand] at a
 * computed document index, [RemoveObjectCommand], [ShiftObjectTreeCommand] for drag-reorder,
 * [RenameObjectRefactorCommand] so a rename propagates into every `provides:` / `requires:` /
 * `context.exports` that names it, and [UpsertAttributeCommand] per field. Nothing here is bespoke: a
 * spec-payload list would have reached none of it because its entries have no location for a nominal
 * reference to resolve to.
 *
 * This document registers nothing. Discovery is graph-wide by inheritance
 * ([ContextConventions.allContexts]), so a declaration reaches the signature picker and the graph-wide
 * duplicate-address check the moment it exists.
 */
class ContextsController(
    props: ContextsControllerProps
):
    RPureComponent<ContextsControllerProps, ContextsControllerState>(props),
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    data class DeclarationRow(
        val location: ObjectLocation,
        val name: String,
        val type: TypeMetadata,
        // The `type` map's raw `generics` notation, kept verbatim so editing the class or nullability
        // round-trips a nested type the picker cannot yet express (LogicTypeOptions has no nested picker).
        val generics: ListAttributeNotation?,
        val qualifier: String,
        val key: String,
        val title: String,
        val icon: String,
        val description: String
    )


    companion object {
        private val typeAttributePath = AttributePath.ofName(ContextConventions.typeAttributeName)

        private val dragHandleColor = Color("rgba(0, 0, 0, 0.45)")
        private val mutedColor = Color("gray")
        private val chipBackgroundColor = Color("rgba(0, 0, 0, 0.06)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ContextsController::class.react {
                        clientStateGlobal = this@Wrapper.clientStateGlobal
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ContextsControllerState.init(props: ContextsControllerProps) {
        documentPath = null
        declarations = null

        editingLocation = null
        editingName = ""
        editingQualifier = ""
        editingKey = ""
        editingTitle = ""
        editingIcon = ""
        editingDescription = ""

        adding = false
        newName = ""
        addError = null

        dragIndex = null
        dropIndex = null
        dropAfter = false
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val graphNotation = clientState.graphStructure().graphNotation

        val documentNotation = graphNotation.documents[documentPath]
            ?: return

        if (! ContextConventions.isContextsDocument(documentNotation)) {
            return
        }

        val mainObjectPath = documentPath.toMainObjectLocation().objectPath

        val newDeclarations = documentNotation
            .directNestedObjectPaths(mainObjectPath, ContextConventions.contextsAttributeName)
            .map { objectPath ->
                val location = ObjectLocation(documentPath, objectPath)

                // descriptorOrNull reads all six declared fields with the nullable overloads and inherits the
                // archetype's defaults. It answers null for an entry that is not a Context at all (only
                // reachable by hand-editing) — such a row still renders, so it can be deleted or repaired
                // rather than becoming invisible.
                val descriptor = ContextConventions.descriptorOrNull(graphNotation, location)

                val typeNotation = graphNotation.firstAttribute(location, typeAttributePath)
                        as? MapAttributeNotation

                DeclarationRow(
                    location = location,
                    name = objectPath.name.value,
                    type = descriptor?.type ?: TypeMetadata.any,
                    generics = typeNotation?.get(TypeMetadataDefiner.genericsKey) as? ListAttributeNotation,
                    qualifier = descriptor?.qualifier ?: "",
                    key = descriptor?.key ?: "",
                    title = descriptor?.title ?: "",
                    icon = descriptor?.icon ?: "",
                    description = descriptor?.description ?: "")
            }

        // map produces a fresh List each fire — guard with structural equality so RPureComponent's shallow
        // state comparison doesn't re-render on unchanged content.
        if (documentPath == state.documentPath && newDeclarations == state.declarations) {
            return
        }

        val navigatedAway = documentPath != state.documentPath

        setState {
            this.documentPath = documentPath
            declarations = newDeclarations

            // Editing and adding are per-document affordances; carrying either across a navigation would
            // leave the form open against a location in a document no longer on screen.
            if (navigatedAway) {
                editingLocation = null
                adding = false
                newName = ""
                addError = null
                dragIndex = null
                dropIndex = null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainObjectPath(): ObjectPath? {
        return state.documentPath?.toMainObjectLocation()?.objectPath
    }


    private fun documentNotation(): DocumentNotation? {
        val documentPath = state.documentPath
            ?: return null
        return props.clientStateGlobal
            .current()
            ?.graphStructure()
            ?.graphNotation
            ?.documents
            ?.get(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        val name = state.newName.trim()
        if (name.isEmpty()) {
            return
        }

        val documentPath = state.documentPath ?: return
        val mainObjectPath = mainObjectPath() ?: return
        val documentNotation = documentNotation() ?: return

        val location = ObjectLocation(
            documentPath,
            mainObjectPath.nest(ContextConventions.contextsAttributePath, ObjectName(name)))

        // AddObjectCommand throws on a name already in the document, and the store surfaces that as an error
        // toast well after the field has closed — check up front so the form can say so in place instead.
        if (location.objectPath in documentNotation.objects.notations.map) {
            setState {
                addError = "'$name' already exists in this document"
            }
            return
        }

        val command = AddObjectCommand(
            location,
            PositionRelation.at(insertionDocumentIndex(documentNotation, mainObjectPath)),
            ObjectNotation.ofParent(ContextConventions.contextObjectName))

        setState {
            adding = false
            newName = ""
            addError = null
        }

        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    // The document index that places a new declaration just after the last existing one, or immediately after
    // `main` when there are none — so the branch stays contiguous and reads in list order on disk.
    private fun insertionDocumentIndex(documentNotation: DocumentNotation, mainObjectPath: ObjectPath): Int {
        val existing = documentNotation.directNestedObjectPaths(
            mainObjectPath, ContextConventions.contextsAttributeName)

        return when {
            existing.isEmpty() ->
                documentNotation.indexOf(mainObjectPath).value + 1

            else ->
                documentNotation.indexOf(existing.last()).value + 1
        }
    }


    private fun onCancelAdd() {
        setState {
            adding = false
            newName = ""
            addError = null
        }
    }


    private fun onRemove(location: ObjectLocation) {
        if (state.editingLocation == location) {
            setState {
                editingLocation = null
            }
        }

        // A declaration is a leaf — nothing nests under it — so a plain remove is enough (contrast a step,
        // whose subtree must be removed deepest-first).
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(location))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartEdit(row: DeclarationRow) {
        setState {
            editingLocation = row.location
            editingName = row.name
            editingQualifier = row.qualifier
            editingKey = row.key
            editingTitle = row.title
            editingIcon = row.icon
            editingDescription = row.description
        }
    }


    private fun onCancelEdit() {
        setState {
            editingLocation = null
        }
    }


    // Commit the text edits and collapse. Every attribute upsert runs against the CURRENT location, and the
    // rename refactor runs last — it moves the object, so it invalidates every location held here.
    private fun onCommitEdit(row: DeclarationRow) {
        val newName = state.editingName.trim()

        // Clearing a field writes an empty scalar rather than removing the attribute: the archetype defaults
        // every one of these to "", so an explicit "" reads identically and needs no second command shape.
        val edits = listOf(
            ContextConventions.qualifierAttributeName to (state.editingQualifier.trim() to row.qualifier),
            ContextConventions.keyAttributeName to (state.editingKey.trim() to row.key),
            ContextConventions.titleAttributeName to (state.editingTitle to row.title),
            ContextConventions.iconAttributeName to (state.editingIcon.trim() to row.icon),
            ContextConventions.descriptionAttributeName to (state.editingDescription to row.description))
            .filter { (_, values) -> values.first != values.second }

        val nameChanged = newName.isNotEmpty() && newName != row.name

        setState {
            editingLocation = null
        }

        if (edits.isEmpty() && ! nameChanged) {
            return
        }

        async {
            for ((attributeName, values) in edits) {
                props.mirroredGraphStore.apply(UpsertAttributeCommand(
                    row.location, attributeName, ScalarAttributeNotation(values.first)))
            }

            if (nameChanged) {
                props.mirroredGraphStore.apply(RenameObjectRefactorCommand(
                    row.location, ObjectName(newName)))
            }
        }
    }


    // Live-applied, like the sibling signature editors: the picker and the toggle each ARE the commit, and
    // holding them locally would only add a way for them to disagree with notation.
    private fun onTypeChange(row: DeclarationRow, className: String, nullable: Boolean) {
        val typeNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(TypeMetadataDefiner.classKey) to ScalarAttributeNotation(className),
            AttributeSegment.ofKey(TypeMetadataDefiner.genericsKey) to
                    (row.generics ?: ListAttributeNotation.empty),
            AttributeSegment.ofKey(TypeMetadataDefiner.nullableKey) to
                    ScalarAttributeNotation(nullable.toString())))

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                row.location, ContextConventions.typeAttributeName, typeNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRowDragOver(index: Int, event: DragEvent<HTMLDivElement>) {
        if (state.dragIndex == null) {
            return
        }
        event.preventDefault()
        val rect = event.currentTarget.getBoundingClientRect()
        val after = event.clientY > rect.top + rect.height / 2
        if (state.dropIndex != index || state.dropAfter != after) {
            setState {
                dropIndex = index
                dropAfter = after
            }
        }
    }


    private fun onReorderDrop() {
        val source = state.dragIndex
        val target = state.dropIndex
        val after = state.dropAfter
        val declarations = state.declarations

        setState {
            dragIndex = null
            dropIndex = null
        }

        if (source == null || target == null || declarations == null) {
            return
        }

        val documentNotation = documentNotation() ?: return

        // The gap (0..size) the cursor points at, counted before the dragged row leaves its slot.
        val insertionIndex = target + (if (after) 1 else 0)

        val position = ObjectTreeReorder.reorderPosition(
            documentNotation.objects.notations.map.keys.toList(),
            declarations.map { it.location.objectPath },
            source,
            insertionIndex)
            ?: return

        async {
            props.mirroredGraphStore.apply(ShiftObjectTreeCommand(
                declarations[source].location,
                position))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val declarations = state.declarations
            ?: return

        div {
            css {
                padding = 1.em
                maxWidth = 64.em
            }

            renderIntro(declarations.isEmpty())

            for ((index, declaration) in declarations.withIndex()) {
                div {
                    key = Key(declaration.name)
                    renderDeclarationBody(declaration, index)
                }
            }

            renderAddControl()
        }
    }


    private fun ChildrenBuilder.renderIntro(isEmpty: Boolean) {
        div {
            css {
                marginBottom = 1.em
                color = mutedColor
                fontSize = 0.9.em
            }

            if (isEmpty) {
                +("No contexts declared yet. A context is a named slot in the ambient scope a run works" +
                        " against — a browser, a database session, a plain value a step needs. Declare one" +
                        " here and it becomes pickable from any script's Provides / Requires rows.")
            }
            else {
                +("Declared here and pickable from any script's Provides / Requires rows. Contexts shipped" +
                        " with kzen-auto, and a plugin's own, are found the same way — this document is one" +
                        " place to put them, not the only one.")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDeclarationBody(row: DeclarationRow, index: Int) {
        div {
            objectLocationMarker(row.location)

            css {
                position = Position.relative
                marginLeft = 1.5.em
                marginBottom = 0.5.em

                // Reveal the drag handle on hover (pure CSS — a hover state field would re-render the whole
                // list on every mouse move).
                "&:hover > [data-drag-handle]" {
                    opacity = number(1.0)
                }
            }

            onDragOver = { event -> onRowDragOver(index, event) }
            onDrop = { event ->
                event.preventDefault()
                onReorderDrop()
            }

            dragHandle(
                isVisible = state.dragIndex == index,
                handleColor = dragHandleColor,
                onStart = { setState { dragIndex = index } },
                onEnd = { setState { dragIndex = null; dropIndex = null } })

            dropIndicator(dropMarkerFor(state.dragIndex, state.dropIndex, state.dropAfter, index))

            if (state.editingLocation == row.location) {
                renderDeclarationEditor(row)
            }
            else {
                renderDeclarationReader(row)
            }
        }
    }


    // The whole `[icon] name : Type qualifier key — description` line reads as one clickable edit
    // affordance: hovering tints it and fades in the pencil, clicking opens the inline editor.
    private fun ChildrenBuilder.renderDeclarationReader(row: DeclarationRow) {
        div {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                borderRadius = 4.px
                paddingLeft = 0.25.em
                paddingRight = 0.25.em
                marginLeft = (-0.25).em
                cursor = Cursor.pointer
                transition = "background-color 120ms ease-out".unsafeCast<Transition>()

                "&:hover" {
                    backgroundColor = chipBackgroundColor
                }
                "&:hover [data-edit-button]" {
                    opacity = number(1.0)
                }
            }

            onClick = { onStartEdit(row) }

            // An empty icon name resolves to a placeholder glyph rather than to nothing, so guard the call
            // instead of relying on the default.
            if (row.icon.isNotEmpty()) {
                span {
                    css {
                        display = Display.inlineFlex
                        alignItems = AlignItems.center
                        marginRight = 0.35.em
                        color = mutedColor
                    }
                    icon(row.icon) {}
                }
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +row.name
            }

            span {
                css {
                    color = mutedColor
                }
                +": ${row.type.toSimple()}"
            }

            // The qualifier names one member of a family and the key is the engine address — both change what
            // the declaration MEANS, so neither may be readable only by opening the editor.
            if (row.qualifier.isNotEmpty()) {
                renderChip("qualifier", row.qualifier)
            }
            if (row.key.isNotEmpty()) {
                renderChip("key", row.key)
            }

            if (row.title.isNotEmpty() && row.title != row.name) {
                span {
                    css {
                        marginLeft = 0.5.em
                        color = mutedColor
                    }
                    +"\"${row.title}\""
                }
            }

            if (row.description.isNotEmpty()) {
                span {
                    css {
                        marginLeft = 0.5.em
                        color = mutedColor
                        fontSize = 0.9.em
                    }
                    +"- ${row.description}"
                }
            }

            div {
                asDynamic()["data-edit-button"] = ""
                css {
                    display = Display.inlineFlex
                    alignItems = AlignItems.center
                    marginLeft = 0.25.em
                    opacity = number(0.0)
                    transition = "opacity 120ms ease-out".unsafeCast<Transition>()
                }

                IconButton {
                    title = "Edit context"
                    size = Size.small
                    icon("material-symbols:edit") {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderChip(label: String, value: String) {
        span {
            this.title = label
            css {
                marginLeft = 0.5.em
                paddingLeft = 0.4.em
                paddingRight = 0.4.em
                borderRadius = 4.px
                backgroundColor = chipBackgroundColor
                fontSize = 0.85.em
                color = mutedColor
            }
            +"$label $value"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDeclarationEditor(row: DeclarationRow) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                // The document page is a blank canvas, so the form may wrap onto a second line rather than
                // being squeezed to fit one (contrast the signature editor, bounded by the step width).
                flexWrap = FlexWrap.wrap
                gap = 0.5.em
                paddingTop = 0.5.em
                paddingBottom = 0.5.em
            }

            renderEditorText(
                row, "Context", 9.em, state.editingName, autoFocusField = true) { text ->
                setState { editingName = text }
            }

            logicTypePicker(
                className = row.type.className.asString(),
                nullable = row.type.nullable,
                onTypeChange = { className, nullable -> onTypeChange(row, className, nullable) },
                onClosedKeyDown = { event ->
                    ClientInputUtils.handleEnterAndEscape(
                        event, { onCommitEdit(row) }, ::onCancelEdit)
                },
                // The form's own flex `gap` already spaces this row's controls.
                itemSpacing = null)

            renderEditorText(
                row, "Qualifier", 7.em, state.editingQualifier) { text -> setState { editingQualifier = text } }

            renderEditorText(
                row, "Key", 7.em, state.editingKey) { text -> setState { editingKey = text } }

            renderEditorText(
                row, "Title", 9.em, state.editingTitle) { text -> setState { editingTitle = text } }

            renderEditorText(
                row, "Icon", 9.em, state.editingIcon) { text -> setState { editingIcon = text } }

            renderEditorText(
                row, "Description", 16.em, state.editingDescription) { text ->
                setState { editingDescription = text }
            }

            IconButton {
                title = "Delete context"
                size = Size.small
                onClick = { onRemove(row.location) }
                icon("material-symbols:delete") {}
            }

            IconButton {
                title = "Cancel edit (Escape)"
                size = Size.small
                onClick = { onCancelEdit() }
                icon("material-symbols:cancel") {}
            }

            IconButton {
                title = "Done (Enter)"
                size = Size.small
                onClick = { onCommitEdit(row) }
                icon("material-symbols:check") {}
            }
        }
    }


    private fun ChildrenBuilder.renderEditorText(
        row: DeclarationRow,
        fieldLabel: String,
        fieldWidth: Length,
        fieldValue: String,
        autoFocusField: Boolean = false,
        onText: (String) -> Unit
    ) {
        span {
            css {
                display = Display.inlineBlock
                width = fieldWidth
            }

            TextField {
                size = Size.small
                fullWidth = true
                autoFocus = autoFocusField
                label = ReactNode(fieldLabel)
                value = fieldValue
                onChange = {
                    onText((it.target as HTMLInputElement).value)
                }
                onKeyDown = { event ->
                    ClientInputUtils.handleEnterAndEscape(
                        event, { onCommitEdit(row) }, ::onCancelEdit)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderAddControl() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginTop = 1.em
            }

            if (state.adding) {
                span {
                    css {
                        display = Display.inlineBlock
                        width = 11.em
                        marginRight = 0.25.em
                    }

                    TextField {
                        size = Size.small
                        autoFocus = true
                        fullWidth = true
                        placeholder = "context name"
                        value = state.newName
                        onChange = {
                            val text = (it.target as HTMLInputElement).value
                            setState {
                                newName = text
                                addError = null
                            }
                        }
                        onKeyDown = { event ->
                            ClientInputUtils.handleEnterAndEscape(event, { onAdd() }, ::onCancelAdd)
                        }
                    }
                }

                IconButton {
                    title = "Add (Enter)"
                    size = Size.small
                    onClick = { onAdd() }
                    icon("material-symbols:check") {}
                }

                IconButton {
                    title = "Cancel (Escape)"
                    size = Size.small
                    onClick = { onCancelAdd() }
                    icon("material-symbols:cancel") {}
                }

                state.addError?.let { message ->
                    span {
                        css {
                            marginLeft = 0.5.em
                            fontSize = 0.85.em
                            color = NamedColor.darkred
                        }
                        +message
                    }
                }
            }
            else {
                IconButton {
                    title = "Add context"
                    size = Size.small
                    onClick = { setState { adding = true } }
                    icon("material-symbols:add-circle-outline") {}
                }

                span {
                    css {
                        fontSize = 0.9.em
                        color = mutedColor
                    }
                    +"Add context"
                }
            }
        }
    }
}
