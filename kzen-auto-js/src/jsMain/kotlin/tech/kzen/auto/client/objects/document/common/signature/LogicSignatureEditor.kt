package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.Switch
import mui.material.TextField
import mui.material.ToggleButton
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.objects.document.common.dragdrop.dropMarkerFor
import tech.kzen.auto.client.objects.document.script.display.dependency.StepDependencyEdges
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRow
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyGutterCellForStep
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.persistentMapOf
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface LogicSignatureEditorProps: Props {
    var objectLocation: ObjectLocation

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore

    // Optional per-parameter run-time values (name -> traced value); rendered next to each row while a
    // run is active. Null/absent when not running (purely presentational — supplied by the controller).
    var parameterValues: Map<String, ExecutionValue>?
}


external interface LogicSignatureEditorState: State {
    var parameters: List<LogicSignatureEditor.ParameterRow>?

    // Dependency lanes for the parameter list (parameters that are referenced by a step are cross-branch
    // sources); rendered in the gutter with the same machinery as the step list.
    var parameterEdges: StepDependencyEdges

    // The parameter currently expanded into the full inline editor (null = all collapsed to readers).
    var editingLocation: ObjectLocation?
    // Local text of the edited parameter's name + default; committed on done so a rename (a refactor) does
    // not fire per keystroke. Initialized when entering edit mode.
    var editingName: String
    var editingDefault: String

    // The collapsed add control expands to an inline name form when true.
    var adding: Boolean
    var newParameterName: String

    // Drag-reorder state (within the parameter list only). dragIndex = the row being dragged; dropIndex +
    // dropAfter = the hovered insertion point.
    var dragIndex: Int?
    var dropIndex: Int?
    var dropAfter: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a Script's typed parameters. Each parameter is a ParameterBinding object in the `parameters`
 * branch (rowless — it has no body step), named by its object name and carrying a `type` TypeMetadata and
 * an optional `default` value. Each parameter renders as a streamlined `name: Type = value` row in the
 * script's dependency column (so a referenced parameter draws a tracing line down to the consuming step),
 * with a pencil to expand the full editor (rename-as-refactor, type, nullable, default, delete) and a drag
 * handle to reorder. Generic type arguments are preserved across edits but are not yet editable here.
 */
class LogicSignatureEditor:
    RPureComponent<LogicSignatureEditorProps, LogicSignatureEditorState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    data class ParameterRow(
        val location: ObjectLocation,
        val name: String,
        val className: String,
        val nullable: Boolean,
        val generics: ListAttributeNotation?,
        val defaultText: String?
    )


    companion object {
        private val typeAttributeName = AttributeName("type")
        private val defaultAttributeName = AttributeName("default")
        private val defaultAttributePath = AttributePath.ofName(defaultAttributeName)
        private const val classKey = "class"
        private const val genericsKey = "generics"
        private const val nullableKey = "nullable"

        private const val parameterBindingArchetype = "ParameterBinding"
        private const val defaultClassName = "kotlin.Any"

        private val dragHandleColor = Color("rgba(0, 0, 0, 0.45)")

        // The selectable simple types + their labels are shared with ResultSignatureEditor (see LogicTypeOptions).

        // Default values are only editable for scalar types the definer can coerce (String/Int/Long/Double/
        // Boolean). List/Set/Any have no default input — they resolve to null when no argument is supplied.
        private val scalarDefaultClassNames = setOf(
            "kotlin.String", "kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Boolean")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun LogicSignatureEditorState.init(props: LogicSignatureEditorProps) {
        parameters = null
        parameterEdges = StepDependencyEdges.EMPTY
        editingLocation = null
        editingName = ""
        editingDefault = ""
        adding = false
        newParameterName = ""
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
        val graphStructure = clientState.graphDefinitionAttempt.graphStructure
        val graphNotation = graphStructure.graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val documentNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?: return

        val parameterLocations = ScriptConventions.orderedDirectChildLocations(
            graphNotation,
            AttributeLocation(props.objectLocation, ScriptConventions.parametersAttributePath))

        val newParameters = parameterLocations.map { location ->
            val typeNotation = graphNotation.firstAttribute(location, typeAttributeName) as? MapAttributeNotation
            val defaultText = (graphNotation.firstAttribute(location, defaultAttributePath)
                    as? ScalarAttributeNotation)
                ?.value

            ParameterRow(
                location = location,
                name = location.objectPath.name.value,
                className = typeNotation?.get(classKey)?.asString() ?: defaultClassName,
                nullable = typeNotation?.get(nullableKey)?.asString()?.toBoolean() ?: false,
                generics = typeNotation?.get(genericsKey) as? ListAttributeNotation,
                defaultText = defaultText)
        }

        // A parameter referenced by a step's code is a cross-branch source; compute its gutter the same way
        // a step branch does (parameter -> step edges come from ScriptDependencyAnalysis including the
        // `parameters` branch).
        val newParameterEdges =
            if (parameterLocations.isEmpty() || !ScriptConventions.isScript(documentNotation)) {
                StepDependencyEdges.EMPTY
            }
            else {
                val analysis = ScriptDependencyAnalysis.analyze(
                    clientState.graphDefinitionAttempt, props.objectLocation.documentPath)
                StepDependencyEdges.compute(parameterLocations, analysis)
            }

        // map produces a fresh List each fire — guard with structural equality so RPureComponent's
        // shallow state comparison doesn't re-render on unchanged content.
        if (newParameters == state.parameters && newParameterEdges == state.parameterEdges) {
            return
        }

        setState {
            parameters = newParameters
            parameterEdges = newParameterEdges
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddParameter() {
        val name = state.newParameterName.trim()
        if (name.isEmpty()) {
            return
        }

        val mainObjectPath = props.objectLocation.objectPath

        val location = ObjectLocation(
            props.objectLocation.documentPath,
            mainObjectPath.nest(
                ScriptConventions.parametersAttributePath, ObjectName(name)))

        // Parameters are the Script header, so place the new binding above main.steps in document order
        // (the serialized "signature") rather than appended at the end. Insert just before the first step,
        // or after main / the existing parameters when there are no steps yet.
        val documentNotation = props.clientStateGlobal.current()
            ?.graphStructure()
            ?.graphNotation
            ?.documents
            ?.get(props.objectLocation.documentPath)

        val insertionRelation =
            if (documentNotation == null) {
                PositionRelation.afterLast
            }
            else {
                val firstStepIndex = documentNotation
                    .directNestedObjectPaths(mainObjectPath, ScriptConventions.stepsAttributeName)
                    .minOfOrNull { documentNotation.indexOf(it).value }

                val insertIndex = firstStepIndex
                    ?: run {
                        val lastHeaderIndex = documentNotation
                            .directNestedObjectPaths(mainObjectPath, ScriptConventions.parametersAttributeName)
                            .maxOfOrNull { documentNotation.indexOf(it).value }
                            ?: documentNotation.indexOf(mainObjectPath).value
                        lastHeaderIndex + 1
                    }

                PositionRelation.at(insertIndex)
            }

        // `type` defaults to Any via the ParameterBinding archetype; set it explicitly via the picker.
        val command = AddObjectCommand(
            location,
            insertionRelation,
            ObjectNotation.ofParent(ObjectName(parameterBindingArchetype)))

        setState {
            adding = false
            newParameterName = ""
        }

        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    private fun onCancelAdd() {
        setState {
            adding = false
            newParameterName = ""
        }
    }


    private fun onRemoveParameter(location: ObjectLocation) {
        if (state.editingLocation == location) {
            setState {
                editingLocation = null
            }
        }
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(location))
        }
    }


    private fun onTypeChange(row: ParameterRow, className: String, nullable: Boolean) {
        val typeNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(classKey) to ScalarAttributeNotation(className),
            AttributeSegment.ofKey(genericsKey) to (row.generics ?: ListAttributeNotation.empty),
            AttributeSegment.ofKey(nullableKey) to ScalarAttributeNotation(nullable.toString())))

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                row.location, typeAttributeName, typeNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartEdit(row: ParameterRow) {
        setState {
            editingLocation = row.location
            editingName = row.name
            editingDefault = row.defaultText ?: ""
        }
    }


    // Commit the text edits (name + default) and collapse. Default is upserted first (on the current
    // location), then the rename refactor runs (it moves the object, so it must be last); both are
    // applied sequentially so the default lands at the pre-rename location.
    private fun onCommitEdit(row: ParameterRow) {
        val newName = state.editingName.trim()
        val newDefault = state.editingDefault.trim()

        val defaultChanged = newDefault != (row.defaultText ?: "")
        val nameChanged = newName.isNotEmpty() && newName != row.name

        setState {
            editingLocation = null
        }

        if (!defaultChanged && !nameChanged) {
            return
        }

        async {
            if (defaultChanged) {
                props.mirroredGraphStore.apply(UpsertAttributeCommand(
                    row.location, defaultAttributeName, ScalarAttributeNotation(newDefault)))
            }
            if (nameChanged) {
                props.mirroredGraphStore.apply(RenameObjectRefactorCommand(
                    row.location, ObjectName(newName)))
            }
        }
    }


    // Discards the deferred text edits (name + default) and collapses. Type/nullable apply live (like the
    // original editor), so they are already committed and not reverted.
    private fun onCancelEdit() {
        setState {
            editingLocation = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onReorderDrop() {
        val source = state.dragIndex
        val target = state.dropIndex
        val after = state.dropAfter
        val parameters = state.parameters

        setState {
            dragIndex = null
            dropIndex = null
        }

        if (source == null || target == null || parameters == null) {
            return
        }

        // insertionIndex is the gap (0..size) the cursor points at; dropping at the dragged row's own two
        // edges is a no-op. Otherwise account for the row leaving its slot when it sits above the target.
        val insertionIndex = target + (if (after) 1 else 0)
        if (insertionIndex == source || insertionIndex == source + 1) {
            return
        }
        val newIndex = if (insertionIndex > source) insertionIndex - 1 else insertionIndex

        val documentNotation = props.clientStateGlobal.current()
            ?.graphStructure()
            ?.graphNotation
            ?.documents
            ?.get(props.objectLocation.documentPath)
            ?: return

        val draggedLocation = parameters[source].location
        val draggedRoot = draggedLocation.objectPath

        // Document order with the dragged subtree removed — the frame the shift index resolves against.
        val remainingPaths = documentNotation.objects.notations.map.keys.filter {
            it != draggedRoot && !it.startsWith(draggedRoot)
        }

        val siblings = parameters
            .filterIndexed { i, _ -> i != source }
            .map { it.location }
        if (siblings.isEmpty()) {
            return
        }

        val anchor = siblings.getOrNull(newIndex)?.objectPath
        val targetDocumentIndex =
            if (anchor != null) {
                remainingPaths.indexOf(anchor)
            }
            else {
                val lastSibling = siblings.last().objectPath
                remainingPaths.indexOfLast { it == lastSibling || it.startsWith(lastSibling) } + 1
            }

        async {
            props.mirroredGraphStore.apply(ShiftObjectTreeCommand(
                draggedLocation,
                PositionRelation.at(targetDocumentIndex)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val parameters = state.parameters
            ?: return

        div {
            css {
                // Take vertical space only when there are parameters; an empty list must not disrupt the page
                // flow — the steps start at the top, with just the floating add control on the right.
                if (parameters.isNotEmpty()) {
                    paddingTop = 1.em
                    marginBottom = 0.5.em
                }
            }

            renderControls()

            val edges = state.parameterEdges
            for ((index, parameter) in parameters.withIndex()) {
                scriptGutterRow(
                    rowLocation = parameter.location,
                    gutter = { stepDependencyGutterCellForStep(index, edges) },
                    body = { renderParameterBody(parameter, index) })
            }
        }
    }


    // The "Parameters" label + add control, floated at the top-right of the script area (absolute, anchored
    // to ScriptController's relative container) so it never adds a row of its own — the parameter list owns
    // all vertical space. The label collapses into an inline name form while adding.
    private fun ChildrenBuilder.renderControls() {
        div {
            css {
                position = Position.absolute
                top = 0.5.em
                right = 0.5.em
                display = Display.flex
                alignItems = AlignItems.center
                // above the step cards (which are positioned but auto z-index) so it stays clickable.
                zIndex = integer(2)
            }

            if (state.adding) {
                span {
                    css {
                        display = Display.inlineBlock
                        width = 9.em
                        marginRight = 0.25.em
                    }

                    TextField {
                        size = Size.small
                        autoFocus = true
                        fullWidth = true
                        placeholder = "parameter name"
                        value = state.newParameterName
                        onChange = {
                            val text = (it.target as HTMLInputElement).value
                            setState { newParameterName = text }
                        }
                        onKeyDown = { event ->
                            ClientInputUtils.handleEnterAndEscape(
                                event, { onAddParameter() }, ::onCancelAdd)
                        }
                    }
                }

                IconButton {
                    title = "Add (Enter)"
                    size = Size.small
                    onClick = { onAddParameter() }
                    icon("material-symbols:check") {}
                }

                IconButton {
                    title = "Cancel (Escape)"
                    size = Size.small
                    onClick = { onCancelAdd() }
                    icon("material-symbols:cancel") {}
                }
            }
            else {
                span {
                    css {
                        fontSize = 0.8.em
                        color = Color("gray")
                        marginRight = 0.25.em
                    }
                    +"Parameters"
                }

                IconButton {
                    title = "Add parameter"
                    size = Size.small
                    onClick = { setState { adding = true } }
                    icon("material-symbols:add-circle-outline") {}
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderParameterBody(parameter: ParameterRow, index: Int) {
        div {
            css {
                position = Position.relative
                marginBottom = 0.25.em

                // Reveal the drag handle on hover (pure CSS — a hover state field would re-render the whole
                // list on every mouse move; see ScriptStepSlot).
                "&:hover > [data-drag-handle]" {
                    opacity = number(1.0)
                }
            }

            // The row is the drop target; constrained to the parameter list (no handlers reach outside it).
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

            if (state.editingLocation == parameter.location) {
                renderParameterEditor(parameter)
            }
            else {
                renderParameterReader(parameter)
            }
        }
    }


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


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderParameterReader(parameter: ParameterRow) {
        // The whole `name: Type = value` reads as one clickable edit affordance: hovering tints it and fades
        // in the pencil, clicking opens the inline editor.
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
                    backgroundColor = Color("rgba(0, 0, 0, 0.06)")
                }
                "&:hover [data-edit-button]" {
                    opacity = number(1.0)
                }
            }

            onClick = { onStartEdit(parameter) }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +parameter.name
            }

            span {
                css {
                    color = Color("gray")
                }
                +": ${typeLabel(parameter)}"
            }

            renderValue(parameter)

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
                    title = "Edit parameter"
                    size = Size.small
                    icon("material-symbols:edit") {}
                }
            }
        }
    }


    // `= value`: the live run-time value if running, else the declared default; nothing when neither exists.
    private fun ChildrenBuilder.renderValue(parameter: ParameterRow) {
        val runtimeValue = props.parameterValues?.get(parameter.name)
        val runtimeText =
            if (runtimeValue != null && runtimeValue !is NullExecutionValue) {
                executionValueText(runtimeValue)
            }
            else {
                null
            }

        val displayText = runtimeText ?: parameter.defaultText?.takeIf { it.isNotEmpty() }
            ?: return

        span {
            css {
                marginLeft = 0.4.em
                color = Color("gray")
            }
            +"= "
            span {
                css {
                    fontWeight = FontWeight.bold
                    color = NamedColor.black
                }
                +displayText
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderParameterEditor(parameter: ParameterRow) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                // Single row — never wrap the trailing done/cancel/delete buttons onto a second line. The
                // field widths below are sized to fit within ScriptController.stepWidth.
                flexWrap = FlexWrap.nowrap
            }

            span {
                css {
                    display = Display.inlineBlock
                    width = 7.5.em
                    marginRight = 0.5.em
                }

                TextField {
                    size = Size.small
                    autoFocus = true
                    fullWidth = true
                    label = ReactNode("Parameter")
                    value = state.editingName
                    onChange = {
                        val text = (it.target as HTMLInputElement).value
                        setState { editingName = text }
                    }
                    onKeyDown = { event ->
                        ClientInputUtils.handleEnterAndEscape(
                            event, { onCommitEdit(parameter) }, ::onCancelEdit)
                    }
                }
            }

            span {
                css {
                    display = Display.inlineBlock
                    width = 8.em
                    marginRight = 0.5.em
                }

                val typeOptions = LogicTypeOptions.classOptions
                    .map { (value, simpleLabel) ->
                        val option: SelectOption = unsafeJso {
                            this.value = value
                            this.label = simpleLabel
                        }
                        option
                    }
                    .toTypedArray()

                muiAutocompleteField(
                    label = "Type",
                    options = typeOptions,
                    selectedOption = typeOptions.find { it.value == parameter.className },
                    onSelect = { onTypeChange(parameter, it.value, parameter.nullable) },
                    disableClearable = true)
            }

            // Nullable as a compact toggle (`?`) rather than a switch + text label — the pressed state IS
            // the meaning, and it reclaims horizontal room on the single editor row.
            ToggleButton {
                value = "nullable"
                selected = parameter.nullable
                size = Size.small
                sx {
                    height = 28.px
                    marginRight = 0.5.em
                }
                title =
                    if (parameter.nullable) {
                        "Nullable (click to require non-null)"
                    }
                    else {
                        "Allow null"
                    }
                onChange = { _, _ -> onTypeChange(parameter, parameter.className, !parameter.nullable) }
                icon("material-symbols:question-mark") {}
            }

            renderDefaultInput(parameter)

            IconButton {
                title = "Delete parameter"
                size = Size.small
                onClick = { onRemoveParameter(parameter.location) }
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
                onClick = { onCommitEdit(parameter) }
                icon("material-symbols:check") {}
            }
        }
    }


    // Default value editor, shown only for scalar types (the definer coerces those). Boolean uses a Switch;
    // the others a text field. The value is held locally and committed on done (see onCommitEdit).
    private fun ChildrenBuilder.renderDefaultInput(parameter: ParameterRow) {
        if (parameter.className !in scalarDefaultClassNames) {
            return
        }

        span {
            css {
                marginRight = 0.5.em
            }
            +"="
        }

        if (parameter.className == "kotlin.Boolean") {
            Switch {
                checked = state.editingDefault == "true"
                onChange = { e, _ -> setState { editingDefault = e.currentTarget.checked.toString() } }
            }
        }
        else {
            span {
                css {
                    display = Display.inlineBlock
                    width = 5.5.em
                    marginRight = 0.5.em
                }

                TextField {
                    size = Size.small
                    fullWidth = true
                    label = ReactNode("Default")
                    value = state.editingDefault
                    onChange = {
                        val text = (it.target as HTMLInputElement).value
                        setState { editingDefault = text }
                    }
                    onKeyDown = { event ->
                        ClientInputUtils.handleEnterAndEscape(
                            event, { onCommitEdit(parameter) }, ::onCancelEdit)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeLabel(parameter: ParameterRow): String =
        LogicTypeOptions.simpleLabel(parameter.className, parameter.nullable)


    private fun executionValueText(value: ExecutionValue): String {
        return when (value) {
            is ScalarExecutionValue -> value.get().toString()
            is ListExecutionValue -> value.values.map { it.get() }.toString()
            else -> value.toString()
        }
    }
}
