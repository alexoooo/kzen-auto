package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.InputLabel
import mui.material.Size
import mui.material.TextField
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.job.JobSummaryStore
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
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
// Which of the two column pickers a transient add form targets (only one is open at a time). Top-level (not
// file-private) because the external interface state property below exposes it.
enum class PivotAddTarget { Rows, Values }


external interface PivotSpecEditorState: State {
    // The Worker's committed pivot config (group-by rows + a column -> aggregate-types map), read from notation.
    var pivotSpec: PivotSpec?

    // The nearest upstream SummaryWorker's live TableSummary (or null pre-run / no Summary upstream) — the source
    // of the candidate columns offered by the row / value pickers (its column keys).
    var upstreamSummary: TableSummary?

    // Transient add form: which section is adding (null = none), and the free-text name used in the no-summary
    // fallback (with a summary, columns are picked from a dropdown).
    var addTarget: PivotAddTarget?
    var addName: String
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a PivotWorker's `pivot` attribute — a PivotSpec, i.e. `rows` (the group-by columns) + `values` (a column
// -> set of aggregate types: Count / Sum / Average / Min / Max). Wired via `editor: PivotSpecEditor` on the Worker
// archetype metadata; the generic DefaultAttributeEditor can't edit a structured map. Reuses the canonical
// PivotSpec command builders (add/remove row, add/remove value column, add/remove value type) — no bespoke command
// code — and reads the committed spec back from notation on every store change. A trimmed Job analogue of Report's
// analysis/pivot controllers (AnalysisPivotRowList / ValueList / ValueType / ValueAdd).
//
// Candidate columns come from the nearest UPSTREAM SummaryWorker's live TableSummary, threaded here through the
// per-document JobSummaryStore (provided by JobController into the DocumentBridge and observed here) — so this
// editor stays free of a restClient and the run-scoped serve query keeps a single owner. With no live Summary
// upstream both pickers degrade to free-text column entry, matching the value-set filter's degrade.
@Suppress("unused")
class PivotSpecEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, PivotSpecEditorState>(props),
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
            PivotSpecEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var summaryStore: JobSummaryStore? = null


    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun PivotSpecEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        pivotSpec = readPivotSpec(graphStructure.graphNotation)
        upstreamSummary = null
        addTarget = null
        addName = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        val store = contextValue<DocumentBridge?>()?.channel(JobSummaryStore.Key)
        summaryStore = store
        store?.observe(this)

        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }

        props.clientStateGlobal.current()?.let { recompute(it.graphStructure()) }
    }


    override fun componentWillUnmount() {
        mounted = false
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


    // Re-derive the committed pivot + the upstream summary and setState only when either changed (value compare,
    // both are data classes) — so an unrelated command or an unchanged summary poll doesn't re-render.
    private fun recompute(graphStructure: GraphStructure) {
        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextPivotSpec = readPivotSpec(graphStructure.graphNotation)
        val nextSummary = computeUpstreamSummary(graphStructure)

        if (state.pivotSpec != nextPivotSpec || state.upstreamSummary != nextSummary) {
            setState {
                pivotSpec = nextPivotSpec
                upstreamSummary = nextSummary
            }
        }
    }


    // Read via mergeAttribute (the deep-merge the Definer uses), so the archetype's `rows: []` / `values: {}`
    // defaults are always present and PivotSpec.ofNotation never trips on a missing sub-key.
    private fun readPivotSpec(graphNotation: GraphNotation): PivotSpec {
        val attributeNotation = graphNotation
            .mergeAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return PivotSpec.empty

        return PivotSpec.ofNotation(attributeNotation)
    }


    private fun computeUpstreamSummary(graphStructure: GraphStructure): TableSummary? {
        val summaryWorker = JobUpstreamSchema
            .nearestUpstreamSummaryWorker(graphStructure, props.objectLocation)
            ?: return null
        return summaryStore?.current()?.get(summaryWorker)
    }


    private fun availableColumns(): List<HeaderLabel> {
        return state.upstreamSummary?.columnSummaries?.map?.keys?.toList() ?: listOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The PivotWorker carries its pivot config as a TOP-LEVEL `pivot` attribute, unlike Report's `analysis.pivot`
    // — so every shared PivotSpec command builder is given this base path (its default targets Report's nesting).
    private fun pivotPath(): AttributePath =
        AttributePath.ofName(props.attributeName)


    // Named apply* (not on*) so they don't shadow child props of the same role inside the react { } blocks.
    private fun applyAddRow(headerLabel: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.addRowCommand(props.objectLocation, headerLabel, pivotPath()))
        }
    }


    private fun applyRemoveRow(headerLabel: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.removeRowCommand(props.objectLocation, headerLabel, pivotPath()))
        }
    }


    private fun applyAddValue(headerLabel: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.addValueCommand(props.objectLocation, headerLabel, pivotPath()))
        }
    }


    private fun applyRemoveValue(headerLabel: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.removeValueCommand(props.objectLocation, headerLabel, pivotPath()))
        }
    }


    private fun applyAddValueType(headerLabel: HeaderLabel, valueType: PivotValueType) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.addValueTypeCommand(props.objectLocation, headerLabel, valueType, pivotPath()))
        }
    }


    private fun applyRemoveValueType(headerLabel: HeaderLabel, valueType: PivotValueType) {
        async {
            props.mirroredGraphStore.apply(
                PivotSpec.removeValueTypeCommand(props.objectLocation, headerLabel, valueType, pivotPath()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applyAdd(target: PivotAddTarget, headerLabel: HeaderLabel) {
        when (target) {
            PivotAddTarget.Rows -> applyAddRow(headerLabel)
            PivotAddTarget.Values -> applyAddValue(headerLabel)
        }
    }


    private fun onAddClick(target: PivotAddTarget) {
        setState {
            addTarget = target
            addName = ""
        }
    }


    private fun onAddCancel() {
        setState {
            addTarget = null
            addName = ""
        }
    }


    private fun onAddFromSummary(target: PivotAddTarget, headerLabel: HeaderLabel) {
        applyAdd(target, headerLabel)
        setState {
            addTarget = null
            addName = ""
        }
    }


    private fun onFreeTextAddSubmit(target: PivotAddTarget) {
        val trimmed = state.addName.trim()
        if (trimmed.isEmpty() || isDuplicate(target, trimmed)) {
            return
        }

        applyAdd(target, HeaderLabel(trimmed, 0))

        setState {
            addTarget = null
            addName = ""
        }
    }


    private fun onAddNameChange(newName: String) {
        setState {
            addName = newName
        }
    }


    private fun handleEnterAndEscape(target: PivotAddTarget, event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, { onFreeTextAddSubmit(target) }, ::onAddCancel)
    }


    // Free-text add can only create an occurrence-0 label, so a name already present at occurrence 0 is a
    // duplicate (an insert on an existing key / list value would fail).
    private fun isDuplicate(target: PivotAddTarget, name: String): Boolean {
        val candidate = HeaderLabel(name, 0)
        val spec = state.pivotSpec
            ?: return false
        return when (target) {
            PivotAddTarget.Rows -> candidate in spec.rows.values
            PivotAddTarget.Values -> candidate in spec.values.columns.keys
        }
    }


    // Types the value toggle group delivers the full selection, so diff against the committed set and apply the
    // single add / remove (mirrors AnalysisPivotValueTypeController).
    private fun onTypeChange(
        headerLabel: HeaderLabel, columnSpec: PivotValueColumnSpec, valueTypes: Array<String>
    ) {
        val oldTypes = columnSpec.types
        val newTypes = valueTypes.map { PivotValueType.valueOf(it) }

        val added = newTypes.filter { it !in oldTypes }
        val removed = oldTypes.filter { it !in newTypes }

        if (added.isNotEmpty()) {
            applyAddValueType(headerLabel, added.first())
        }
        else if (removed.isNotEmpty()) {
            applyRemoveValueType(headerLabel, removed.first())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val pivotSpec = state.pivotSpec
            ?: return

        InputLabel {
            sx {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        renderRows(pivotSpec)
        renderValues(pivotSpec)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRows(pivotSpec: PivotSpec) {
        div {
            css {
                marginTop = 0.25.em
            }

            sectionLabel("Rows")

            for (row in pivotSpec.rows.values) {
                div {
                    key = Key(row.asString())
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                    }

                    IconButton {
                        title = "Remove row"
                        size = Size.small
                        onClick = { applyRemoveRow(row) }
                        icon("material-symbols:delete") {}
                    }

                    span { +row.render() }
                }
            }

            val unused = availableColumns().filter { it !in pivotSpec.rows.values }
            renderAdd(PivotAddTarget.Rows, unused, "row")
        }
    }


    private fun ChildrenBuilder.renderValues(pivotSpec: PivotSpec) {
        div {
            css {
                marginTop = 0.5.em
            }

            sectionLabel("Values")

            for ((column, columnSpec) in pivotSpec.values.columns) {
                div {
                    key = Key(column.asString())
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        marginBottom = 0.25.em
                    }

                    IconButton {
                        title = "Remove value"
                        size = Size.small
                        onClick = { applyRemoveValue(column) }
                        icon("material-symbols:delete") {}
                    }

                    span {
                        css {
                            marginRight = 0.5.em
                        }
                        +column.render()
                    }

                    renderTypeToggle(column, columnSpec)
                }
            }

            val unused = availableColumns().filter { it !in pivotSpec.values.columns.keys }
            renderAdd(PivotAddTarget.Values, unused, "value")
        }
    }


    private fun ChildrenBuilder.renderTypeToggle(headerLabel: HeaderLabel, columnSpec: PivotValueColumnSpec) {
        ToggleButtonGroup {
            exclusive = false
            size = Size.small
            value = columnSpec.types.map { it.name }.toTypedArray()

            asDynamic()["onChange"] = { _, v ->
                onTypeChange(headerLabel, columnSpec, v)
            }

            for (valueType in PivotValueType.entries) {
                ToggleButton {
                    key = Key(valueType.name)
                    value = valueType.name
                    size = Size.small
                    +valueType.name
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.sectionLabel(text: String) {
        div {
            css {
                fontWeight = FontWeight.bold
                fontSize = 0.9.em
                color = NamedColor.gray
            }
            +text
        }
    }


    // Add affordance for a section: a summary-driven dropdown of unused columns when available, else a free-text
    // column field (so a pivot can be configured before any run has produced a summary).
    private fun ChildrenBuilder.renderAdd(target: PivotAddTarget, unused: List<HeaderLabel>, noun: String) {
        div {
            if (state.addTarget == target) {
                if (unused.isNotEmpty()) {
                    renderAddFromSummary(target, unused)
                }
                else {
                    renderFreeTextAdd(target)
                }
                renderAddCancel()
            }
            else {
                renderAddButton(target, noun)
            }
        }
    }


    private fun ChildrenBuilder.renderAddButton(target: PivotAddTarget, noun: String) {
        div {
            title = "Add $noun"
            css {
                display = Display.inlineBlock
            }

            IconButton {
                size = Size.small
                onClick = { onAddClick(target) }
                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.renderAddFromSummary(target: PivotAddTarget, unused: List<HeaderLabel>) {
        val selectOptions = unused
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
                onSelect = { onAddFromSummary(target, HeaderLabel.ofString(it.value)) },
                disableClearable = true,
                autoFocus = true,
                openOnFocus = true)
        }
    }


    private fun ChildrenBuilder.renderFreeTextAdd(target: PivotAddTarget) {
        val trimmed = state.addName.trim()
        val duplicate = trimmed.isNotEmpty() && isDuplicate(target, trimmed)

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
                    val fieldTarget = it.target as HTMLInputElement
                    onAddNameChange(fieldTarget.value)
                }

                error = duplicate

                onKeyDown = { e ->
                    handleEnterAndEscape(target, e)
                }
            }
        }

        IconButton {
            title = "Add"
            size = Size.small
            onClick = { onFreeTextAddSubmit(target) }
            icon("material-symbols:add-circle-outline") {}
        }
    }


    private fun ChildrenBuilder.renderAddCancel() {
        div {
            css {
                display = Display.inlineBlock
            }

            IconButton {
                title = "Cancel adding"
                size = Size.small
                onClick = { onAddCancel() }
                icon("material-symbols:cancel") {}
            }
        }
    }
}
