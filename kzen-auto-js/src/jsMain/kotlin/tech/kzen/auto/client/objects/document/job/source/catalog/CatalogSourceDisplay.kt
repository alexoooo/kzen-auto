package tech.kzen.auto.client.objects.document.job.source.catalog

import emotion.react.css
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.summary
import react.dom.html.ReactHTML.span
import react.Key
import web.html.checkbox
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.display.*
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.catalog.CatalogConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentList
import web.cssom.*
import web.html.InputType

class CatalogSourceDisplay(props: CatalogSourceDisplayProps):
    RPureComponent<CatalogSourceDisplayProps, CatalogSourceDisplayState>(props),
    ClientStateGlobal.Observer, CatalogStore.Observer
{
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi
    ): WorkerDisplayWrapper(objectLocation) {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            CatalogSourceDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                attributeViewManager = this@Wrapper.attributeViewManager
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                restClient = this@Wrapper.restClient
                block()
            }
        }
    }

    private val store = CatalogStore(props.restClient) { props.common.objectLocation }
    private val scope = MainScope()
    override fun observedObjectLocation() = props.common.objectLocation

    override fun CatalogSourceDisplayState.init(props: CatalogSourceDisplayProps) {
        catalogState = null; selection = emptyList(); symbols = emptyList()
        symbolSearch = ""; saving = false; error = null
        datesOpen = null; symbolsOpen = false; dateFilter = ""; dateSearch = ""
    }
    override fun componentDidMount() { props.clientStateGlobal.observe(this); store.mount(this) }
    override fun componentWillUnmount() { store.unmount(); scope.cancel(); props.clientStateGlobal.unobserve(this) }
    override fun onCatalogState(state: CatalogStore.State) { setState { catalogState = state } }

    override fun onClientState(clientState: ClientState) {
        val notation = clientState.graphStructure().graphNotation
        fun values(name: String) = (notation.firstAttribute(props.common.objectLocation, AttributeName(name)) as? ListAttributeNotation)
            ?.values?.map { requireNotNull(it.asString()) } ?: emptyList()
        val selection = values(CatalogConventions.selection)
        val symbols = values(CatalogConventions.symbols)
        if (selection == state.selection && symbols == state.symbols) return
        setState { this.selection = selection; this.symbols = symbols }
    }

    private fun save(name: String, values: List<String>) {
        if (props.common.active || state.saving) return
        setState { saving = true; error = null }
        scope.launch {
            try {
                val result = props.mirroredGraphStore.apply(UpsertAttributeCommand(props.common.objectLocation,
                    AttributeName(name), ListAttributeNotation(values.distinct().sorted().map(::ScalarAttributeNotation).toPersistentList())))
                if (result is MirroredGraphError) setState { error = result.error.message }
            }
            catch (e: Exception) { setState { error = e.message ?: "Could not save selection" } }
            finally { setState { saving = false } }
        }
    }

    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            common = props.common
            attributeEditorManager = props.attributeEditorManager
            attributeViewManager = props.attributeViewManager
            clientStateGlobal = props.clientStateGlobal
            mirroredGraphStore = props.mirroredGraphStore
            hiddenAttributes = setOf(AttributeName(CatalogConventions.selection), AttributeName(CatalogConventions.symbols))
            attributeDisclosure = "Advanced"
            bodyBefore = { it.renderCatalog() }
        }
    }

    private fun ChildrenBuilder.renderCatalog() {
        val catalog = state.catalogState?.catalog
        val entries = catalog?.entries.orEmpty()
        val selected = entries.filter { it.id in state.selection }
        val preparing = selected.any { it.state in preparationStates }
        val busy = state.catalogState?.busy == true
        val editingDisabled = props.common.active || state.saving
        val datesOpen = state.datesOpen ?: state.selection.isEmpty()
        div {
            css {
                display = Display.flex; flexDirection = FlexDirection.column; gap = 0.5.em
                "button" {
                    fontFamily = Globals.inherit; fontSize = 0.85.em; padding = Padding(4.px, 8.px)
                    border = Border(1.px, LineStyle.solid, Color("#c4c4c4")); borderRadius = 4.px
                    backgroundColor = NamedColor.white; color = Color("#245a91"); cursor = Cursor.pointer
                    "&:disabled" { color = Color("#888"); cursor = Cursor.default }
                }
                "td" { padding = Padding(5.px, 4.px); verticalAlign = VerticalAlign.top }
                "th" { textAlign = TextAlign.left; padding = Padding(5.px, 4.px); color = Color("#666") }
            }
            div {
                css { display = Display.flex; gap = 6.px; alignItems = AlignItems.center; flexWrap = FlexWrap.wrap }
                button {
                    onClick = { setState { this.datesOpen = !datesOpen } }
                    +(if (datesOpen) "▾ Dates" else "▸ Dates")
                }
                span {
                    val dates = selected.map { it.date }
                    +(if (state.selection.isEmpty()) "Choose dates" else
                        dates.take(3).joinToString(", ") + if (dates.size > 3) " +${dates.size - 3}" else "")
                }
                if (selected.isNotEmpty()) span {
                    css { color = Color("#687080"); fontSize = 0.85.em }
                    +"${selected.count { it.state == "ready" }}/${state.selection.size} ready"
                }
            }
            if (datesOpen) {
                div {
                    css { display = Display.flex; gap = 5.px; flexWrap = FlexWrap.wrap }
                    val filter = state.dateFilter.ifEmpty { if (state.selection.isEmpty()) "All" else "Selected" }
                    listOf("Selected", "Downloaded", "All").forEach { option ->
                        button {
                            disabled = option == filter
                            onClick = { setState { dateFilter = option } }
                            +option
                        }
                    }
                    input {
                        placeholder = "Find date"; value = state.dateSearch
                        onChange = { event -> setState { dateSearch = event.currentTarget.value } }
                    }
                    button { disabled = busy; onClick = { store.action("refresh") }; +"Refresh dates" }
                }
                if (catalog == null) div { +"Loading dates…" }
                val filter = state.dateFilter.ifEmpty { if (state.selection.isEmpty()) "All" else "Selected" }
                val visible = entries.filter {
                    (filter == "All" || filter == "Selected" && it.id in state.selection || filter == "Downloaded" && it.downloaded) &&
                            it.date.contains(state.dateSearch)
                }.sortedByDescending { it.date }
                if (catalog != null && visible.isEmpty()) div { +"No dates match this view." }
                if (visible.isNotEmpty()) div {
                    css { maxHeight = 280.px; overflowY = Auto.auto }
                    table {
                        css { width = 100.pct; borderCollapse = BorderCollapse.collapse; fontSize = 0.85.em }
                        thead { tr { listOf("Date", "Download", "Preparation").forEach { th { +it } } } }
                        tbody {
                            visible.forEach { entry ->
                                tr {
                                    key = Key(entry.id)
                                    css { borderBottom = Border(1.px, LineStyle.solid, Color("#e5e7eb")) }
                                    td {
                                        label {
                                            input {
                                                type = InputType.checkbox; checked = entry.id in state.selection; disabled = editingDisabled
                                                onChange = { save(CatalogConventions.selection,
                                                    if (entry.id in state.selection) state.selection - entry.id else state.selection + entry.id) }
                                            }
                                            +entry.date
                                        }
                                        details {
                                            summary { css { cursor = Cursor.pointer; color = Color("#245a91") }; +"Source" }
                                            div { +entry.fileName }
                                            a { css { overflowWrap = OverflowWrap.anywhere }; href = entry.sourceUrl; +entry.sourceUrl }
                                        }
                                    }
                                    td {
                                        +(if (entry.downloaded) "Downloaded" else "Not downloaded")
                                        div { css { color = Color("#687080") }; +formatSize(entry.sizeBytes) }
                                    }
                                    td {
                                        +(if (entry.state == "missing" || entry.state == "downloaded") "Not prepared"
                                            else entry.state.replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div {
                css { display = Display.flex; gap = 5.px; flexWrap = FlexWrap.wrap }
                if (selected.any { it.state != "ready" && it.state !in preparationStates }) button {
                    disabled = busy
                    onClick = { store.action("prepare", state.selection) }
                    +"Download and prepare"
                }
                if (preparing) button {
                    disabled = busy
                    onClick = { store.action("cancel", state.selection) }
                    +"Cancel preparation"
                }
            }
            selected.filter { it.state in preparationStates || it.state == "failed" }.forEach { entry ->
                div {
                    css { fontSize = 0.85.em; color = Color(if (entry.state == "failed") "#b3261e" else "#687080") }
                    +"${entry.date}: ${entry.state} — ${entry.detail}"
                    if (entry.state == "downloading") +" · ${formatSize(entry.completedBytes)} / ${formatSize(entry.sizeBytes)}"
                }
            }
            val missing = state.selection.filter { id -> entries.none { it.id == id } }
            if (catalog != null && missing.isNotEmpty()) div { +"Selected files unavailable: ${missing.joinToString()}" }
            if (props.common.active) div { css { fontSize = 0.8.em }; +"Date and symbol selection is fixed for this run." }
            renderSymbols(editingDisabled)
            listOfNotNull(state.error, state.catalogState?.error, catalog?.error).distinct().forEach { message ->
                div { css { color = Color("#b3261e") }; +message }
            }
        }
    }

    private fun ChildrenBuilder.renderSymbols(disabled: Boolean) {
        val selectedEntries = state.catalogState?.catalog?.entries.orEmpty().filter { it.id in state.selection }
        val available = selectedEntries.flatMap { it.symbols }.distinct().sorted()
        div {
            div {
                css { display = Display.flex; gap = 6.px; alignItems = AlignItems.center; flexWrap = FlexWrap.wrap }
                button {
                    onClick = { setState { symbolsOpen = !state.symbolsOpen } }
                    +(if (state.symbolsOpen) "▾ Symbols" else "▸ Symbols")
                }
                span { +(if (state.symbols.isEmpty()) "All symbols" else
                    state.symbols.take(6).joinToString(", ") + if (state.symbols.size > 6) " +${state.symbols.size - 6}" else "") }
            }
            if (state.symbolsOpen) {
                div {
                    css { display = Display.flex; gap = 5.px; flexWrap = FlexWrap.wrap; marginTop = 6.px }
                    button { this.disabled = disabled || state.symbols.isEmpty(); onClick = { save(CatalogConventions.symbols, emptyList()) }; +"Use all symbols" }
                    input { placeholder = "Find symbols"; value = state.symbolSearch; onChange = { event -> setState { symbolSearch = event.currentTarget.value } } }
                }
                if (state.symbols.isNotEmpty()) div {
                    css { display = Display.flex; gap = 4.px; flexWrap = FlexWrap.wrap; marginTop = 5.px }
                    state.symbols.forEach { symbol ->
                        button { this.disabled = disabled; title = "Remove $symbol"; onClick = { save(CatalogConventions.symbols, state.symbols - symbol) }; +"$symbol ×" }
                    }
                }
                if (available.isEmpty()) div { +"Symbol choices appear after preparation." }
                val matches = available.filter { it.contains(state.symbolSearch, ignoreCase = true) }
                div {
                    css { maxHeight = 180.px; overflowY = Auto.auto; marginTop = 5.px }
                    matches.take(symbolChoiceLimit).forEach { symbol ->
                        div {
                            key = Key(symbol)
                            css { display = Display.flex; gap = 6.px; alignItems = AlignItems.baseline }
                            label {
                                input {
                                    type = InputType.checkbox; checked = symbol in state.symbols; this.disabled = disabled
                                    onChange = { save(CatalogConventions.symbols, if (symbol in state.symbols) state.symbols - symbol else state.symbols + symbol) }
                                }
                                +symbol
                            }
                            details {
                                summary { css { cursor = Cursor.pointer; fontSize = 0.8.em; color = Color("#687080") }; +"Dates" }
                                +selectedEntries.filter { symbol in it.symbols }.joinToString { it.date }
                            }
                        }
                    }
                }
                if (matches.size > symbolChoiceLimit) div { +"Showing $symbolChoiceLimit of ${matches.size}. Search to narrow the list." }
            }
            state.symbols.forEach { symbol ->
                val absent = selectedEntries.filter { it.state == "ready" && symbol !in it.symbols }
                if (absent.isNotEmpty()) div { css { fontSize = 0.85.em }; +"$symbol is absent on ${absent.joinToString { it.date }}; those combinations will be skipped." }
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "Size unknown"
        bytes < bytesPerKiB -> "$bytes B"
        bytes < bytesPerMiB -> "${bytes / bytesPerKiB} KiB"
        else -> "${bytes / bytesPerMiB} MiB"
    }

    companion object {
        private val preparationStates = setOf("queued", "downloading", "verifying", "preparing")
        private const val bytesPerKiB = 1024
        private const val bytesPerMiB = 1024 * 1024
        private const val symbolChoiceLimit = 100
    }
}
