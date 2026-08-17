package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import mui.material.InputLabel
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.spec.sort.SortColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.sort.SortSpec
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.VerticalAlign
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SortSpecEditorState: State {
    // The Worker's committed sort keys, in PRIORITY order (the notation map's insertion order). Value-compared
    // on refresh so an unrelated command elsewhere doesn't re-render the rows.
    var columns: List<SortColumnSpec>?
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a SortWorker's `sort` attribute — a SortSpec, i.e. an ORDERED column -> ascending map where the map order
// IS the multi-key sort priority (first key primary, ties broken by the next). Wired via `editor: SortSpecEditor`
// in the SortWorker archetype metadata; the generic DefaultAttributeEditor renders a structured map attribute as
// "type not supported", so the SortTool ribbon insert needs this dedicated editor. Reuses the canonical SortSpec
// command builders to mutate the map; reads the committed spec back from notation on every store change.
//
// Each row is a committed { column, direction } pair: toggling the direction fires a command immediately (no
// in-progress text to debounce, unlike FormulaMapRow), so the rows are stateless and rendered inline. Columns are
// added by name (a new key defaults to ascending, appended at LOWEST priority) — free-text entry, the documented
// fallback until upstream-schema threading lands (P4i JobController schema plumbing), matching how the value-set
// filter editor degrades. Re-prioritizing an existing key (a ShiftInAttributeCommand move) is a documented
// follow-up; for now priority is add-order (delete + re-add to move a key to the tail).
@Suppress("unused")
class SortSpecEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, SortSpecEditorState>(props),
    LocalGraphStore.Observer
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
            SortSpecEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SortSpecEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        columns = readColumns(graphNotation)
    }


    private fun readColumns(graphNotation: GraphNotation): List<SortColumnSpec> {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return listOf()

        return SortSpec.ofNotation(attributeNotation).columns
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refreshColumns(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refreshColumns(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    // Pick up add / remove / direction toggle (and any external edit) of the sort keys. Value-equality gated
    // (SortColumnSpec is a data class) so an unrelated command elsewhere in the document doesn't re-render.
    private fun refreshColumns(graphNotation: GraphNotation) {
        if (props.objectLocation !in graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextColumns = readColumns(graphNotation)
        if (state.columns != nextColumns) {
            setState {
                columns = nextColumns
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Named apply* (not on*) so they don't shadow child props of the same role inside the react { } blocks.
    private fun applyAdd(column: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                SortSpec.addCommand(props.objectLocation, column))
        }
    }


    private fun applyToggleDirection(column: HeaderLabel, ascending: Boolean) {
        async {
            props.mirroredGraphStore.apply(
                SortSpec.updateAscendingCommand(props.objectLocation, column, ascending))
        }
    }


    private fun applyDelete(column: HeaderLabel) {
        async {
            props.mirroredGraphStore.apply(
                SortSpec.removeCommand(props.objectLocation, column))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val columns = state.columns
            ?: return

        InputLabel {
            sx {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        div {
            for ((priority, sortColumn) in columns.withIndex()) {
                div {
                    key = Key(sortColumn.column.asString())
                    css {
                        marginBottom = 0.25.em
                    }
                    renderRow(priority, sortColumn)
                }
            }
        }

        renderAdd(columns)
    }


    // One committed sort key: [delete] [direction toggle] priority. columnName — aligned in a table like
    // FormulaMapRow, but stateless (the direction toggle commits immediately).
    private fun ChildrenBuilder.renderRow(priority: Int, sortColumn: SortColumnSpec) {
        val column = sortColumn.column

        table {
            css {
                marginLeft = 0.25.em
            }
            tbody {
                tr {
                    td {
                        IconButton {
                            title = "Remove sort key"
                            onClick = {
                                applyDelete(column)
                            }
                            icon("material-symbols:delete") {}
                        }
                    }

                    td {
                        IconButton {
                            title =
                                if (sortColumn.ascending) {
                                    "Ascending (click for descending)"
                                }
                                else {
                                    "Descending (click for ascending)"
                                }
                            onClick = {
                                applyToggleDirection(column, !sortColumn.ascending)
                            }
                            if (sortColumn.ascending) {
                                icon("material-symbols:arrow-upward") {}
                            }
                            else {
                                icon("material-symbols:arrow-downward") {}
                            }
                        }
                    }

                    td {
                        css {
                            verticalAlign = VerticalAlign.middle
                        }
                        +"${priority + 1}. ${column.render()}"
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderAdd(columns: List<SortColumnSpec>) {
        AddNameForm::class.react {
            entityLabel = "sort key"
            fieldLabel = "Sort column name"
            isDuplicate = { name -> columns.any { it.column == HeaderLabel(name, 0) } }
            onAdd = { name -> applyAdd(HeaderLabel(name, 0)) }
        }
    }
}
