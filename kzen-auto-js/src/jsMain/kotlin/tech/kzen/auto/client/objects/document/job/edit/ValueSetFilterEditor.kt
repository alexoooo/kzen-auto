package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Checkbox
import mui.material.IconButton
import mui.material.InputLabel
import mui.material.Size
import mui.material.TextField
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import react.ChildrenBuilder
import react.Key
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.job.JobSummaryStore
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.summary.ColumnSummary
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface ValueSetFilterEditorState: State {
    // The Worker's committed value-set filter (column -> {type, whitelisted values}), read from notation.
    var filterSpec: FilterSpec?

    // The nearest upstream SummaryWorker's live TableSummary (or null pre-run / when no Summary is upstream) —
    // the source of each column's candidate distinct values (its NominalValueSummary histogram).
    var upstreamSummary: TableSummary?

    // Transient add-column form (ephemeral UI): `adding` toggles it open; `addName` holds the free-text name
    // used only in the no-summary fallback (with a summary, columns are picked from a dropdown).
    var adding: Boolean
    var addName: String
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a ValueSetFilterWorker's `filter` attribute — a FilterSpec, i.e. a column -> {type, values} whitelist
// (RequireAny keeps a record whose field is one of the listed values, ExcludeAll drops it). Wired via
// `editor: ValueSetFilterEditor` on the Worker archetype metadata; the generic DefaultAttributeEditor can't edit
// a structured map, so the palette insert needs this dedicated editor. Reuses the canonical FilterSpec command
// builders (add/remove column, add/remove value, update type) — no bespoke command code — and reads the committed
// spec back from notation on every store change (the same shape as Report's FilterItemController).
//
// Distinct-value discovery: a column's candidate values come from the nearest UPSTREAM SummaryWorker's live
// TableSummary, threaded here through the per-document JobSummaryStore (provided by JobController into the
// DocumentBridge and observed here) rather than a per-editor serve query — so this editor stays free of a
// restClient and the run-scoped channel query has a single owner. With no live Summary upstream it degrades to
// free-text value / column entry, exactly as Report's filter does when its summary hasn't loaded.
@Suppress("unused")
class ValueSetFilterEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, ValueSetFilterEditorState>(props),
    LocalGraphStore.Observer,
    JobSummaryStore.Observer
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
            ValueSetFilterEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Looked up from the DocumentBridge in componentDidMount (owner-provided by JobController), so it may be null
    // for the brief window before the controller's first render — recompute defensively treats null as "no data".
    private var summaryStore: JobSummaryStore? = null


    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ValueSetFilterEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        filterSpec = readFilterSpec(graphStructure.graphNotation)
        upstreamSummary = null
        adding = false
        addName = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        val store = contextValue<DocumentBridge?>()?.lookup(JobSummaryStore.Key)
        summaryStore = store
        store?.observe(this)

        async {
            props.mirroredGraphStore.observe(this)
        }

        // The store isn't re-pushed on observe; pick up any summaries already present before this editor mounted.
        props.clientStateGlobal.current()?.let { recompute(it.graphStructure()) }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        summaryStore?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        recompute(graphDefinition.graphStructure)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        recompute(graphDefinitionAttempt.graphStructure)
    }


    override fun onJobSummaries(summaries: Map<ObjectLocation, TableSummary>) {
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return
        recompute(graphStructure)
    }


    // Re-derive the committed filter + the upstream summary and setState only when either changed (value compare,
    // both are data classes) — so an unrelated command or an unchanged summary poll doesn't re-render.
    private fun recompute(graphStructure: GraphStructure) {
        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextFilterSpec = readFilterSpec(graphStructure.graphNotation)
        val nextSummary = computeUpstreamSummary(graphStructure)

        if (state.filterSpec != nextFilterSpec || state.upstreamSummary != nextSummary) {
            setState {
                filterSpec = nextFilterSpec
                upstreamSummary = nextSummary
            }
        }
    }


    private fun readFilterSpec(graphNotation: GraphNotation): FilterSpec {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return FilterSpec(mapOf())

        return FilterSpec.ofNotation(attributeNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun computeUpstreamSummary(graphStructure: GraphStructure): TableSummary? {
        val summaryWorker = JobUpstreamSchema
            .nearestUpstreamSummaryWorker(graphStructure, props.objectLocation)
            ?: return null
        return summaryStore?.current()?.get(summaryWorker)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Named apply* (not on*) so they don't shadow child props of the same role inside the react { } blocks.
    // columnKey is a HeaderLabel.asString() ("occurrence|text"), the notation map key FilterSpec.Definer expects.
    private fun applyAddColumn(columnKey: String) {
        async {
            props.mirroredGraphStore.apply(
                FilterSpec.addCommand(props.objectLocation, columnKey))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddClick() {
        setState {
            adding = true
            addName = ""
        }
    }


    private fun onAddCancel() {
        setState {
            adding = false
            addName = ""
        }
    }


    private fun onAddFromSummary(columnKey: String) {
        applyAddColumn(columnKey)
        setState {
            adding = false
            addName = ""
        }
    }


    private fun onFreeTextAddSubmit() {
        val trimmed = state.addName.trim()
        if (trimmed.isEmpty() || isDuplicate(trimmed)) {
            return
        }

        applyAddColumn(HeaderLabel(trimmed, 0).asString())

        setState {
            adding = false
            addName = ""
        }
    }


    private fun onAddNameChange(newName: String) {
        setState {
            addName = newName
        }
    }


    private fun handleEnterAndEscape(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onFreeTextAddSubmit, ::onAddCancel)
    }


    // Free-text add can only create an occurrence-0 label, so a name already present at occurrence 0 is a
    // duplicate (an InsertMapEntryInAttributeCommand on an existing key would fail).
    private fun isDuplicate(name: String): Boolean {
        val candidate = HeaderLabel(name, 0)
        return state.filterSpec?.columns?.keys?.contains(candidate) ?: false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val filterSpec = state.filterSpec
            ?: return
        val summary = state.upstreamSummary

        InputLabel {
            css {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        for ((column, columnFilterSpec) in filterSpec.columns) {
            div {
                key = Key(column.asString())
                ValueSetFilterColumn::class.react {
                    this.objectLocation = props.objectLocation
                    this.columnName = column
                    this.columnFilterSpec = columnFilterSpec
                    this.columnSummary = summary?.columnSummaries?.map?.get(column)
                    this.mirroredGraphStore = props.mirroredGraphStore
                }
            }
        }

        renderAdd(filterSpec, summary)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Add-column affordance: when an upstream Summary offers columns not yet filtered, pick one from a dropdown
    // (Report's FilterAddController pattern); otherwise fall back to free-text column entry (SortSpecEditor's
    // pattern) so a filter can still be configured before any run has produced a summary.
    private fun ChildrenBuilder.renderAdd(filterSpec: FilterSpec, summary: TableSummary?) {
        val availableColumns = (summary?.columnSummaries?.map?.keys ?: setOf())
            .filter { it !in filterSpec.columns }

        div {
            if (state.adding) {
                if (availableColumns.isNotEmpty()) {
                    renderAddFromSummary(availableColumns)
                }
                else {
                    renderFreeTextAdd(filterSpec)
                }
                renderAddCancel()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add column filter"
            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAddClick()
                }
                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.renderAddFromSummary(availableColumns: List<HeaderLabel>) {
        val selectOptions = availableColumns
            .map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }
            .toTypedArray()

        div {
            css {
                display = Display.inlineBlock
                width = 15.em
            }

            muiAutocompleteField(
                label = "Column name",
                options = selectOptions,
                selectedOption = null,
                onSelect = { onAddFromSummary(it.value) },
                disableClearable = true,
                autoFocus = true,
                openOnFocus = true)
        }
    }


    private fun ChildrenBuilder.renderFreeTextAdd(filterSpec: FilterSpec) {
        val trimmed = state.addName.trim()
        val duplicate = trimmed.isNotEmpty() && filterSpec.columns.keys.contains(HeaderLabel(trimmed, 0))

        div {
            css {
                display = Display.inlineBlock
                width = 15.em
            }

            TextField {
                label = ReactNode("Column name")
                fullWidth = true
                size = Size.small

                onChange = {
                    val target = it.target as HTMLInputElement
                    onAddNameChange(target.value)
                }

                error = duplicate

                onKeyDown = { e ->
                    handleEnterAndEscape(e)
                }
            }
        }

        IconButton {
            title = "Add column filter"
            onClick = {
                onFreeTextAddSubmit()
            }
            icon("material-symbols:add-circle-outline") {}
        }
    }


    private fun ChildrenBuilder.renderAddCancel() {
        div {
            css {
                display = Display.inlineBlock
            }

            IconButton {
                title = "Cancel adding column filter"
                onClick = {
                    onAddCancel()
                }
                icon("material-symbols:cancel") {}
            }
        }
    }
}


//=====================================================================================================================
external interface ValueSetFilterColumnProps: Props {
    var objectLocation: ObjectLocation
    var columnName: HeaderLabel
    var columnFilterSpec: ColumnFilterSpec
    var columnSummary: ColumnSummary?
    var mirroredGraphStore: MirroredGraphStore
}


external interface ValueSetFilterColumnState: State {
    var open: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// One configured column of a value-set filter — a whitelist over its distinct values. Header shows the column
// name, its total count (from the upstream Summary, when available), and delete / expand controls; the expanded
// detail is a RequireAny / ExcludeAll toggle plus the value set, editable both as histogram checkboxes (from the
// Summary's NominalValueSummary) and as free text (MultiTextAttributeEditor over the same `values` list). A
// trimmed Job analogue of Report's FilterItemController, applying the same FilterSpec command builders.
class ValueSetFilterColumn(
    props: ValueSetFilterColumnProps
):
    RComponent<ValueSetFilterColumnProps, ValueSetFilterColumnState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxValueLength = 96
        private const val abbreviationSuffix = "…"


        private fun formatCount(count: Long): String {
            return count.toString()
                .replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
        }


        private fun abbreviateValue(value: String): String {
            if (value.isBlank()) {
                return "(blank)"
            }
            if (value.length < maxValueLength) {
                return value
            }
            return value.substring(0, maxValueLength - abbreviationSuffix.length) + abbreviationSuffix
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ValueSetFilterColumnState.init(props: ValueSetFilterColumnProps) {
        // Start expanded when there are already values to review; a freshly-added (empty) column starts collapsed.
        open = props.columnFilterSpec.values.isNotEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applyRemoveColumn() {
        async {
            props.mirroredGraphStore.apply(
                FilterSpec.removeCommand(props.objectLocation, props.columnName))
        }
    }


    private fun applyType(type: ColumnFilterType) {
        async {
            props.mirroredGraphStore.apply(
                FilterSpec.updateTypeCommand(props.objectLocation, props.columnName, type))
        }
    }


    private fun applyToggleValue(value: String, add: Boolean) {
        async {
            val command =
                if (add) {
                    FilterSpec.addValueCommand(props.objectLocation, props.columnName, value)
                }
                else {
                    FilterSpec.removeValueCommand(props.objectLocation, props.columnName, value)
                }
            props.mirroredGraphStore.apply(command)
        }
    }


    private fun onOpenToggle() {
        val toggle = ! state.open
        setState {
            open = toggle
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                borderTop = Border(1.px, LineStyle.solid, NamedColor.gainsboro)
                marginTop = 0.25.em
                paddingTop = 0.25.em
            }

            renderHeader()

            if (state.open) {
                div {
                    css {
                        marginLeft = 1.em
                    }
                    renderType()
                    renderValues()
                    renderHistogram()
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +props.columnName.render()
            }

            props.columnSummary?.let {
                span {
                    css {
                        marginLeft = 0.5.em
                        color = NamedColor.gray
                        fontSize = 0.85.em
                    }
                    +"Count: ${formatCount(it.count)}"
                }
            }

            div {
                css {
                    flexGrow = number(1.0)
                }
            }

            IconButton {
                title = "Remove column filter"
                size = Size.small
                onClick = { applyRemoveColumn() }
                icon("material-symbols:delete") {}
            }

            IconButton {
                title = if (state.open) "Collapse" else "Expand"
                size = Size.small
                onClick = { onOpenToggle() }
                if (state.open) {
                    icon("material-symbols:expand-less") {}
                }
                else {
                    icon("material-symbols:expand-more") {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderType() {
        ToggleButtonGroup {
            value = props.columnFilterSpec.type.name
            exclusive = true

            asDynamic()["onChange"] = { _, v ->
                if (v is String) {
                    applyType(ColumnFilterType.valueOf(v))
                }
            }

            ToggleButton {
                value = ColumnFilterType.RequireAny.name
                size = Size.small
                +"Require any"
            }

            ToggleButton {
                value = ColumnFilterType.ExcludeAll.name
                size = Size.small
                +"Exclude all"
            }
        }
    }


    private fun ChildrenBuilder.renderValues() {
        div {
            css {
                marginTop = 0.5.em
            }

            MultiTextAttributeEditor::class.react {
                labelOverride = "Filter values"
                maxRows = 10
                disabled = false

                objectLocation = props.objectLocation
                attributePath = FilterSpec.columnValuesAttributePath(props.columnName)
                mirroredGraphStore = props.mirroredGraphStore

                value = props.columnFilterSpec.values
                unique = true
            }
        }
    }


    // Distinct-value histogram from the upstream Summary: each value is a checkbox toggling its membership in the
    // whitelist. Absent (no Summary upstream / not yet run) → the free-text values editor above is the only path.
    private fun ChildrenBuilder.renderHistogram() {
        val histogram = props.columnSummary?.nominalValueSummary?.histogram
        if (histogram.isNullOrEmpty()) {
            return
        }

        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                marginTop = 0.5.em
            }

            table {
                thead {
                    tr {
                        for (heading in listOf("Filter", "Value", "Count")) {
                            th {
                                key = Key(heading)
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = NamedColor.white
                                    zIndex = integer(1)
                                    textAlign = TextAlign.left
                                }
                                +heading
                            }
                        }
                    }
                }

                tbody {
                    for (entry in histogram.entries) {
                        val checked = props.columnFilterSpec.values.contains(entry.key)
                        tr {
                            key = Key(entry.key)

                            td {
                                Checkbox {
                                    this.checked = checked
                                    onChange = { _, _ ->
                                        applyToggleValue(entry.key, ! checked)
                                    }
                                }
                            }

                            td {
                                +abbreviateValue(entry.key)
                            }

                            td {
                                +formatCount(entry.value)
                            }
                        }
                    }
                }
            }
        }
    }
}
