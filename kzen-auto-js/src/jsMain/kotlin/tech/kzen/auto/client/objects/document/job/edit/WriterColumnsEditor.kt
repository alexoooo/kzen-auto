package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Checkbox
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.file.FileTableColors
import tech.kzen.auto.client.objects.document.common.file.fileTableGrid
import tech.kzen.auto.client.objects.document.common.file.fileTableHeaderCell
import tech.kzen.auto.client.objects.document.common.file.fileTableScrollFrame
import tech.kzen.auto.client.objects.document.job.JobValidationChannel
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.job.path.ColumnPatterns
import tech.kzen.auto.common.objects.document.job.path.ContractPathTree
import tech.kzen.auto.common.objects.document.job.path.PathBinding
import tech.kzen.auto.common.objects.document.job.path.PathBindingResult
import tech.kzen.auto.common.objects.document.job.path.PathProjectionEntry
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.objects.document.job.path.ProjectionPath
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.renderName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface WriterColumnsEditorState: State {
    // The writer's committed column selection; value-compared on refresh.
    var spec: WriterColumnSpec?

    // The upstream Worker's output contract per the Job's server-side validation (null: [upstreamNote] says why),
    // and the path columns bound against it — output names and errors come from here.
    var upstream: DataContract?
    var upstreamNote: String?
    var binding: PathBindingResult?

    // Whether the table shows (a one-line summary otherwise), and the field trees opened in it (by path text)
    var open: Boolean
    var expanded: Set<String>
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a writer's `columns` attribute (a WriterColumnSpec, an ordered list of columns) as a table, collapsed by
// default to the names of the columns written. Each payload column under `*` is a row with a checkbox, kept or left
// out by the Allow and Exclude patterns beside the table (a checkbox writes its column's exact-name line, so the
// patterns are the whole selection), and an output name; a path column (a metadata field such as `meta.name`, or
// a nested scalar) is a row too, with its alias and binding error. `meta` and a payload record open into their
// fields, a scalar of which is checked to add it as a path column; a writer never unnests. Wired via
// `editor: WriterColumnsEditor` on the `columns` metadata of Format, CsvWriterWorker and ExportWriterWorker.
@Suppress("unused")
class WriterColumnsEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, WriterColumnsEditorState>(props),
    LocalGraphStore.Observer,
    JobValidationChannel.Observer
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
            WriterColumnsEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val mutedText = Color("rgba(0, 0, 0, 0.6)")
        private val errorText = Color("#c62828")
    }


    /** A payload column's name (as a header shows it) and type, and the field it opens into, if it opens. */
    private class PayloadField(
        val name: String,
        val type: String,
        val tree: ContractPathTree.Candidate?
    )


    /** A row of the table: one output column, or one left out, in output order. */
    private sealed interface ColumnRow {
        class Payload(
            val columnIndex: Int,
            val column: WriterColumn.PayloadFields,
            val field: PayloadField,
            val position: Int?
        ): ColumnRow

        class Path(
            val columnIndex: Int,
            val entry: PathProjectionEntry,
            val position: Int
        ): ColumnRow
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var validationChannel: JobValidationChannel? = null
    private var mounted = false


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun WriterColumnsEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        spec = readSpec(graphStructure.graphNotation)
        upstream = null
        upstreamNote = null
        binding = null
        open = false
        expanded = setOf()
    }


    override fun componentDidMount() {
        mounted = true
        val bridge = contextValue<DocumentBridge?>()
        validationChannel = bridge?.channel(JobValidationChannel.Key)?.also { channel ->
            channel.observe(this)
            props.clientStateGlobal.current()?.graphStructure()?.let { refresh(it, channel.current()) }
        }
        async {
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
        validationChannel?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refresh(graphDefinition.graphStructure, validationChannel?.current())
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refresh(graphDefinitionAttempt.graphStructure, validationChannel?.current())
    }


    override fun onJobValidation(validation: JobValidation?) {
        props.clientStateGlobal.current()?.graphStructure()?.let { refresh(it, validation) }
    }


    private fun readSpec(graphNotation: GraphNotation): WriterColumnSpec {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? ListAttributeNotation
            ?: return WriterColumnSpec.payloadFields
        return try {
            WriterColumnSpec.ofNotation(attributeNotation)
        }
        catch (_: IllegalArgumentException) {
            WriterColumnSpec.payloadFields
        }
    }


    // Re-read the columns and the upstream contract; value-gated so an unrelated command doesn't re-render
    private fun refresh(graphStructure: GraphStructure, validation: JobValidation?) {
        val graphNotation = graphStructure.graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }
        val nextSpec = readSpec(graphNotation)
        val (nextUpstream, note) = JobUpstreamSchema.upstreamContract(graphStructure, props.objectLocation, validation)
        val nextBinding = nextUpstream?.let { PathBinding.bind(PathProjectionSpec(nextSpec.pathEntries()), it) }
        if (state.spec != nextSpec || state.upstream != nextUpstream ||
                state.upstreamNote != note || state.binding != nextBinding) {
            setState {
                spec = nextSpec
                upstream = nextUpstream
                upstreamNote = note
                binding = nextBinding
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val attributePath: AttributePath
        get() = AttributePath.ofName(props.attributeName)


    private fun apply(command: NotationCommand) {
        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    private fun applyAdd(path: ProjectionPath) {
        val spec = state.spec
            ?: return
        apply(WriterColumnSpec.addPathCommand(props.objectLocation, props.attributeName, spec, path))
    }


    private fun applyRemove(index: Int) {
        apply(PathProjectionSpec.removeCommand(props.objectLocation, index, attributePath))
    }


    private fun applyAlias(index: Int, entry: PathProjectionEntry, alias: String) {
        apply(PathProjectionSpec.aliasCommand(props.objectLocation, index, entry.path, alias, attributePath))
    }


    // [columnIndex] -1 is the implicit `*` of an empty list
    private fun applyPayloadFields(columnIndex: Int, column: WriterColumn.PayloadFields) {
        apply(WriterColumnSpec.payloadFieldsCommand(props.objectLocation, props.attributeName, columnIndex, column))
    }


    private fun toggleOpen() {
        val next = !state.open
        setState { open = next }
    }


    private fun toggleExpanded(pathText: String) {
        val current = state.expanded
        val next = if (pathText in current) current - pathText else current + pathText
        setState { expanded = next }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The writer's columns in order; no columns is an implicit `*`, numbered -1
    private fun columnsOf(spec: WriterColumnSpec): List<IndexedValue<WriterColumn>> =
        if (spec.columns.isEmpty()) listOf(IndexedValue(-1, WriterColumn.PayloadFields.every))
        else spec.columns.withIndex().toList()


    // The payload's columns, as ColumnProjectionDescriptor projects them: a record's fields, or a scalar's `value`
    private fun payloadFields(upstream: DataContract): List<PayloadField> {
        val trees = ContractPathTree.roots(upstream).associateBy { it.path.asString() }
        return when (val structural = upstream.payload().expanded().structural) {
            is DataType.Record -> structural.fields.map { field ->
                val name = HeaderLabel(field.id.name, field.id.occurrence).render()
                val tree = trees[field.id.name]
                    ?.takeIf { field.id.occurrence == 0 && opens(it) }
                PayloadField(name, typeLabel(field.type), tree)
            }
            is DataType.Scalar -> listOf(PayloadField("value", typeLabel(structural), null))
            else -> listOf()
        }
    }


    // A record opens into its fields; a list or map would unnest, which a writer column never does
    private fun opens(candidate: ContractPathTree.Candidate): Boolean =
        candidate.kind == ContractPathTree.Kind.Record || candidate.kind is ContractPathTree.Kind.Reference


    private fun typeLabel(type: DataType): String =
        when (type) {
            is DataType.Scalar -> type.kind.renderName()
            is DataType.Record -> "record"
            is DataType.Listing -> "list"
            is DataType.Mapping -> "map"
            is DataType.Reference -> "↻ ${type.id.value}"
            else -> PathBinding.describe(type)
        }


    private fun rows(spec: WriterColumnSpec, payload: List<PayloadField>): List<ColumnRow> {
        val rows = ArrayList<ColumnRow>()
        var position = 0
        for ((columnIndex, column) in columnsOf(spec)) {
            when (column) {
                is WriterColumn.PayloadFields -> for (field in payload) {
                    val kept = column.patterns.included(field.name)
                    rows += ColumnRow.Payload(columnIndex, column, field, if (kept) ++position else null)
                }
                is WriterColumn.Path -> rows += ColumnRow.Path(columnIndex, column.entry, ++position)
            }
        }
        return rows
    }


    private fun errors(spec: WriterColumnSpec): List<String> {
        val patternErrors = columnsOf(spec)
            .mapNotNull { it.value as? WriterColumn.PayloadFields }
            .flatMap { it.patterns.errors() }
        val bindingErrors = state.binding?.errors.orEmpty().map { "${it.path.asString()}: ${it.message}" }
        return patternErrors + bindingErrors
    }


    private fun summary(spec: WriterColumnSpec, payload: List<PayloadField>?): String {
        if (payload == null || payload.isEmpty()) {
            if (spec.isPayloadFieldsOnly()) {
                return "every payload column"
            }
            return columnsOf(spec).joinToString(", ") { (_, column) ->
                when (column) {
                    is WriterColumn.PayloadFields -> patternSummary(column.patterns)
                    is WriterColumn.Path -> column.entry.outputName
                }
            }
        }
        val names = rows(spec, payload).mapNotNull { row ->
            when (row) {
                is ColumnRow.Payload -> row.position?.let {
                    val output = row.column.outputName(row.field.name)
                    if (output == row.field.name) output else "${row.field.name} → $output"
                }
                is ColumnRow.Path -> row.entry.path.asString().let {
                    if (it == row.entry.outputName) it else "$it → ${row.entry.outputName}"
                }
            }
        }
        return names.joinToString(", ").ifEmpty { "no columns" }
    }


    private fun patternSummary(patterns: ColumnPatterns): String {
        val parts = listOfNotNull(
            patterns.allow.takeIf { it.isNotEmpty() }?.let { "allow ${it.joinToString(" ")}" },
            patterns.exclude.takeIf { it.isNotEmpty() }?.let { "exclude ${it.joinToString(" ")}" })
        return if (parts.isEmpty()) "every payload column" else "payload columns: ${parts.joinToString(", ")}"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val spec = state.spec
            ?: return
        val upstream = state.upstream
        val payload = upstream?.let { payloadFields(it) }

        renderHeader(spec, payload)
        if (!state.open) {
            return
        }

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.flexStart
                gap = 1.em
                marginTop = 0.25.em
            }
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }
                if (upstream == null) {
                    div {
                        css {
                            fontSize = 0.85.em
                            color = mutedText
                        }
                        +(state.upstreamNote ?: "Upstream contract not available")
                    }
                }
                else {
                    renderTable(spec, upstream, payload.orEmpty())
                }
            }
            div {
                css {
                    width = 18.em
                    flexShrink = number(0.0)
                }
                renderPatterns(spec)
            }
        }
    }


    private fun ChildrenBuilder.renderHeader(spec: WriterColumnSpec, payload: List<PayloadField>?) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                cursor = Cursor.pointer
            }
            onClick = { toggleOpen() }
            IconButton {
                size = Size.small
                title = if (state.open) "Collapse" else "Expand"
                icon(if (state.open) "material-symbols:expand-more" else "material-symbols:chevron-right") {}
            }
            span {
                css {
                    fontSize = 0.8.em
                    color = mutedText
                    marginRight = 0.75.em
                    whiteSpace = WhiteSpace.nowrap
                }
                +CommonEditUtils.formattedLabel(attributePath)
            }
            // The summary or the table, not both
            if (!state.open) {
                val text = summary(spec, payload)
                span {
                    css {
                        fontSize = 0.85.em
                        overflow = Overflow.hidden
                        textOverflow = TextOverflow.ellipsis
                        whiteSpace = WhiteSpace.nowrap
                    }
                    title = text
                    +text
                }
            }
        }
        for (error in errors(spec)) {
            div {
                css {
                    fontSize = 0.8.em
                    color = errorText
                    marginLeft = 2.5.em
                }
                +error
            }
        }
    }


    private fun ChildrenBuilder.renderTable(spec: WriterColumnSpec, upstream: DataContract, payload: List<PayloadField>) {
        val chosen = spec.columns.withIndex()
            .mapNotNull { (index, column) -> (column as? WriterColumn.Path)?.let { it.entry.path.asString() to index } }
            .toMap()
        val metadata = ContractPathTree.metadataRoot(upstream)

        div {
            css {
                fileTableScrollFrame(24.em)
            }
            table {
                css {
                    fileTableGrid()
                    fontSize = 0.9.em
                }
                thead {
                    tr {
                        fileTableHeaderCell {}
                        fileTableHeaderCell { +"#" }
                        fileTableHeaderCell { +"Column" }
                        fileTableHeaderCell { +"Type" }
                        fileTableHeaderCell { +"As" }
                    }
                }
                tbody {
                    for (row in rows(spec, payload)) {
                        when (row) {
                            is ColumnRow.Payload -> renderPayloadRow(row, upstream, chosen)
                            is ColumnRow.Path -> renderPathRow(row, upstream)
                        }
                    }
                    if (metadata != null) {
                        renderTreeRow(upstream, metadata, 0, chosen)
                    }
                }
            }
        }
        if (spec.columns.isNotEmpty() && spec.columns.none { it is WriterColumn.PayloadFields }) {
            Button {
                variant = ButtonVariant.text
                size = Size.small
                onClick = {
                    apply(WriterColumnSpec.addPayloadFieldsCommand(props.objectLocation, props.attributeName))
                }
                +"Add every payload column (*)"
            }
        }
    }


    private fun ChildrenBuilder.renderPayloadRow(
        row: ColumnRow.Payload,
        upstream: DataContract,
        chosen: Map<String, Int>
    ) {
        val name = row.field.name
        val kept = row.position != null
        val toggled = row.column.patterns.toggled(name, !kept)
        val blockedBy = if (toggled == null) row.column.patterns.excludedBy(name) else null
        val tree = row.field.tree
        val open = tree != null && tree.path.asString() in state.expanded

        tr {
            key = Key("${row.columnIndex}:*:$name")
            css {
                hover {
                    backgroundColor = FileTableColors.hoverRow
                }
            }
            td {
                span {
                    title = blockedBy?.let { "Excluded by the pattern \"$it\"" }
                        ?: if (kept) "Leave $name out" else "Write $name"
                    Checkbox {
                        size = Size.small
                        checked = kept
                        disabled = toggled == null
                        onChange = { _, _ ->
                            toggled?.let { applyPayloadFields(row.columnIndex, row.column.withPatterns(it)) }
                        }
                    }
                }
            }
            positionCell(row.position)
            td {
                css {
                    width = 100.pct
                    paddingLeft = 0.5.em
                    fontWeight = if (kept) FontWeight.bold else FontWeight.normal
                    if (!kept) {
                        color = mutedText
                    }
                }
                div {
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                    }
                    +name
                    if (tree != null) {
                        expandButton(tree.path.asString(), open)
                    }
                }
            }
            typeCell(row.field.type)
            td {
                css {
                    paddingRight = 0.5.em
                }
                ColumnDraftField::class.react {
                    value = row.column.renames[name] ?: ""
                    placeholder = name
                    width = 10.em
                    onCommit = { alias -> applyPayloadFields(row.columnIndex, row.column.withRename(name, alias.trim())) }
                }
            }
        }
        if (tree != null && open) {
            for (child in ContractPathTree.children(upstream, tree)) {
                renderTreeRow(upstream, child, 1, chosen)
            }
        }
    }


    private fun ChildrenBuilder.renderPathRow(row: ColumnRow.Path, upstream: DataContract) {
        val pathText = row.entry.path.asString()
        val error = state.binding?.errors?.firstOrNull { it.path == row.entry.path }?.message
        val type = (PathBinding.resolve(upstream, row.entry.path) as? PathBinding.Resolution.At)
            ?.let { typeLabel(it.contract.expanded().structural) }

        tr {
            key = Key("${row.columnIndex}:$pathText")
            css {
                hover {
                    backgroundColor = FileTableColors.hoverRow
                }
            }
            td {
                Checkbox {
                    size = Size.small
                    checked = true
                    title = "Leave $pathText out"
                    onChange = { _, _ -> applyRemove(row.columnIndex) }
                }
            }
            positionCell(row.position)
            td {
                css {
                    width = 100.pct
                    paddingLeft = 0.5.em
                    fontWeight = FontWeight.bold
                }
                span {
                    css {
                        fontFamily = FontFamily.monospace
                    }
                    +pathText
                }
                error?.let { message ->
                    div {
                        css {
                            fontSize = 0.85.em
                            fontWeight = FontWeight.normal
                            color = errorText
                        }
                        +message
                    }
                }
            }
            typeCell(type ?: "")
            td {
                css {
                    paddingRight = 0.5.em
                }
                ColumnDraftField::class.react {
                    value = row.entry.alias ?: ""
                    placeholder = row.entry.path.defaultOutputName()
                    width = 10.em
                    onCommit = { alias -> applyAlias(row.columnIndex, row.entry, alias.trim()) }
                }
            }
        }
    }


    // A field that isn't a column yet, below `meta` or a payload record: a scalar is checked to add it
    private fun ChildrenBuilder.renderTreeRow(
        upstream: DataContract,
        candidate: ContractPathTree.Candidate,
        depth: Int,
        chosen: Map<String, Int>
    ) {
        val pathText = candidate.path.asString()
        val opens = opens(candidate)
        val open = opens && pathText in state.expanded
        val chosenIndex = chosen[pathText]

        tr {
            key = Key("tree:$pathText")
            css {
                hover {
                    backgroundColor = FileTableColors.hoverRow
                }
            }
            td {
                if (candidate.selectable && !opens) {
                    Checkbox {
                        size = Size.small
                        checked = chosenIndex != null
                        title = if (chosenIndex != null) "Leave $pathText out" else "Write $pathText"
                        onChange = { _, _ ->
                            if (chosenIndex != null) applyRemove(chosenIndex) else applyAdd(candidate.path)
                        }
                    }
                }
            }
            positionCell(null)
            td {
                css {
                    width = 100.pct
                    paddingLeft = (0.5 + depth * 1.25).em
                    color = mutedText
                }
                div {
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        fontFamily = FontFamily.monospace
                    }
                    +(if (depth == 0) pathText else candidate.label)
                    if (opens) {
                        expandButton(pathText, open)
                    }
                }
            }
            typeCell(kindLabel(candidate.kind))
            td {}
        }
        if (open) {
            for (child in ContractPathTree.children(upstream, candidate)) {
                renderTreeRow(upstream, child, depth + 1, chosen)
            }
        }
    }


    private fun kindLabel(kind: ContractPathTree.Kind): String =
        when (kind) {
            is ContractPathTree.Kind.Leaf -> kind.scalar.renderName()
            ContractPathTree.Kind.Record -> "record"
            ContractPathTree.Kind.List -> "list"
            ContractPathTree.Kind.Map -> "map"
            is ContractPathTree.Kind.Reference -> "↻ ${kind.id}"
            is ContractPathTree.Kind.Unsupported -> kind.description
        }


    private fun ChildrenBuilder.expandButton(pathText: String, open: Boolean) {
        IconButton {
            size = Size.small
            title = if (open) "Hide its fields" else "Show its fields"
            onClick = { toggleExpanded(pathText) }
            icon(if (open) "material-symbols:expand-more" else "material-symbols:chevron-right") {}
        }
    }


    private fun ChildrenBuilder.positionCell(position: Int?) {
        td {
            css {
                paddingLeft = 0.5.em
                paddingRight = 0.5.em
                color = NamedColor.gray
            }
            position?.let { +"$it" }
        }
    }


    private fun ChildrenBuilder.typeCell(type: String) {
        td {
            css {
                paddingLeft = 0.5.em
                paddingRight = 0.5.em
                whiteSpace = WhiteSpace.nowrap
                color = mutedText
            }
            +type
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The Allow and Exclude patterns of the first `*` (Report's Analysis layout); a list without `*` has none
    private fun ChildrenBuilder.renderPatterns(spec: WriterColumnSpec) {
        val (columnIndex, column) = columnsOf(spec)
            .firstOrNull { it.value is WriterColumn.PayloadFields }
            ?.let { it.index to it.value as WriterColumn.PayloadFields }
            ?: return

        ColumnDraftField::class.react {
            key = Key("allow:$columnIndex")
            value = column.patterns.allow.joinToString("\n")
            label = "Allow patterns (one per line)"
            multiline = true
            error = column.patterns.errors().any { it.startsWith("Allow") }
            onCommit = { text ->
                applyPayloadFields(columnIndex, column.withPatterns(ColumnPatterns(lines(text), column.patterns.exclude)))
            }
        }
        div {
            css {
                height = 0.75.em
            }
        }
        ColumnDraftField::class.react {
            key = Key("exclude:$columnIndex")
            value = column.patterns.exclude.joinToString("\n")
            label = "Exclude patterns (one per line)"
            multiline = true
            error = column.patterns.errors().any { it.startsWith("Exclude") }
            onCommit = { text ->
                applyPayloadFields(columnIndex, column.withPatterns(ColumnPatterns(column.patterns.allow, lines(text))))
            }
        }
    }


    private fun lines(text: String): List<String> =
        text.split("\n").filter { it.isNotBlank() }
}
