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
        val busy = state.catalogState?.busy == true
        val editingDisabled = props.common.active || state.saving
        div {
            css {
                display = Display.flex; flexDirection = FlexDirection.column; gap = 0.75.em
                "button" {
                    fontFamily = Globals.inherit; fontSize = 0.8.em; padding = Padding(5.px, 8.px)
                    border = Border(1.px, LineStyle.solid, Color("#c4c4c4")); borderRadius = 4.px
                    backgroundColor = NamedColor.white; color = Color("#245a91"); cursor = Cursor.pointer
                    "&:disabled" { color = Color("#888"); cursor = Cursor.default }
                }
                "td" { padding = Padding(7.px, 4.px); verticalAlign = VerticalAlign.top }
                "th" { textAlign = TextAlign.left; padding = Padding(5.px, 4.px); color = Color("#666") }
            }
            div {
                css { display = Display.flex; flexWrap = FlexWrap.wrap; gap = 5.px }
                button { disabled = busy; onClick = { store.action("refresh") }; +"Refresh dates" }
                button {
                    disabled = busy || state.selection.isEmpty()
                    onClick = { store.action("prepare", state.selection) }
                    +"Download and prepare selected"
                }
                button {
                    disabled = busy || state.selection.isEmpty()
                    onClick = { store.action("cancel", state.selection) }
                    +"Cancel preparation"
                }
            }
            if (catalog == null) div { +"Loading dates…" }
            if (catalog != null && entries.isEmpty()) div { +"No dated files found. Refresh the download catalog." }
            div {
                css { overflowX = Auto.auto; maxHeight = 350.px; overflowY = Auto.auto }
                table {
                    css { borderCollapse = BorderCollapse.collapse; width = 100.pct; tableLayout = TableLayout.fixed; fontSize = 0.8.em }
                    thead { tr { listOf("Date / source", "Download", "Analysis").forEach { th { +it } } } }
                    tbody {
                        entries.forEach { entry ->
                            tr {
                                key = Key(entry.id)
                                css { borderBottom = Border(1.px, LineStyle.solid, Color("#ddd")) }
                                td {
                                    label {
                                        css { whiteSpace = WhiteSpace.nowrap; fontWeight = FontWeight.bold }
                                        input {
                                            type = InputType.checkbox; checked = entry.id in state.selection; disabled = editingDisabled
                                            onChange = { save(CatalogConventions.selection,
                                                if (entry.id in state.selection) state.selection - entry.id else state.selection + entry.id) }
                                        }
                                        +entry.date
                                    }
                                    div { css { overflowWrap = OverflowWrap.anywhere; color = Color("#666"); marginTop = 3.px }; +entry.fileName }
                                    details {
                                        summary { css { color = Color("#245a91"); cursor = Cursor.pointer }; +"Source URL" }
                                        a {
                                            css { overflowWrap = OverflowWrap.anywhere }
                                            href = entry.sourceUrl; title = entry.sourceUrl; +entry.sourceUrl
                                        }
                                    }
                                }
                                td {
                                    div { +(if (entry.downloaded) "Downloaded" else "Not downloaded") }
                                    div { css { color = Color("#666") }; +formatSize(entry.sizeBytes) }
                                }
                                td {
                                    div {
                                        css { color = Color(if (entry.state == "ready") "#237747" else if (entry.state == "failed") "#b3261e" else "#444"); fontWeight = FontWeight.bold }
                                        +(if (entry.state == "missing" || entry.state == "downloaded") "Not prepared" else entry.state.replaceFirstChar { it.uppercase() })
                                    }
                                    div { css { fontSize = 0.9.em; color = Color("#666") }; +entry.detail }
                                    if (entry.state == "downloading") {
                                        div { +"${formatSize(entry.completedBytes)} downloaded" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val missing = state.selection.filter { id -> entries.none { it.id == id } }
            if (missing.isNotEmpty()) div { +"Selected files unavailable: ${missing.joinToString()}" }
            if (state.selection.isEmpty()) div { +"Select at least one date." }
            else if (entries.any { it.id in state.selection && it.state != "ready" }) div { +"Prepare the selected dates before running." }
            if (props.common.active) div { +"Date and symbol selection is fixed for this run." }
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
            div { +"Symbols: ${if (state.symbols.isEmpty()) "All" else state.symbols.joinToString()}" }
            button { this.disabled = disabled || state.symbols.isEmpty(); onClick = { save(CatalogConventions.symbols, emptyList()) }; +"Use all symbols" }
            input { css { marginLeft = 6.px; padding = 4.px; maxWidth = 150.px }; placeholder = "Find symbols"; value = state.symbolSearch; onChange = { event -> setState { symbolSearch = event.currentTarget.value } } }
            if (available.isEmpty()) div { +"Symbol choices appear after preparation." }
            div {
                css { maxHeight = 180.px; overflowY = Auto.auto }
                available.filter { it.contains(state.symbolSearch, ignoreCase = true) }.take(symbolChoiceLimit).forEach { symbol ->
                    val dates = selectedEntries.filter { symbol in it.symbols }.map { it.date }
                    div { label {
                        input {
                            type = InputType.checkbox; checked = symbol in state.symbols; this.disabled = disabled
                            onChange = { save(CatalogConventions.symbols, if (symbol in state.symbols) state.symbols - symbol else state.symbols + symbol) }
                        }
                        +"$symbol · ${dates.joinToString()}"
                    } }
                }
            }
            if (available.size > symbolChoiceLimit) div { +"Search to narrow the symbol list." }
            state.symbols.forEach { symbol ->
                val absent = selectedEntries.filter { it.state == "ready" && symbol !in it.symbols }
                if (absent.isNotEmpty()) div { +"$symbol is absent on ${absent.joinToString { it.date }}; those combinations will be skipped." }
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
        private const val bytesPerKiB = 1024
        private const val bytesPerMiB = 1024 * 1024
        private const val symbolChoiceLimit = 100
    }
}
