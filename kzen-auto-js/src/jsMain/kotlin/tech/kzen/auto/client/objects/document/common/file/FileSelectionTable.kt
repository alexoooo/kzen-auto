package tech.kzen.auto.client.objects.document.common.file

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Checkbox
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.util.data.DataLocation
import web.cssom.*


/**
 * Presentation-only table of an ordered file selection, styled as Report's selected-files table.
 *
 * Checking rows is what the actions above the table operate on — remove and reorder — so this deliberately mirrors
 * [FileBrowser]: click anywhere on a row to check it, shift-click to extend a range. Ordering is the one thing
 * Report's selection does not have, so position is a column rather than an implicit fact about row order.
 */
external interface FileSelectionTableProps: Props {
    var entries: List<FileSelectionEntry>
    var checked: Set<DataLocation>
    var showDetails: Boolean
    var perEntryFormat: Boolean

    // Null while the catalogue is still in flight (or if it failed): the selects then offer only Default and
    // whatever the row already holds, so an existing override still reads correctly and nothing is lost.
    var formatCatalog: FileFormatCatalog?

    var onCheckedChanged: (Set<DataLocation>) -> Unit
    var onFormatChanged: (Int, String) -> Unit
    var onEncodingChanged: (Int, String) -> Unit
}


external interface FileSelectionTableState: State {
    var rangeAnchor: Int?
}


class FileSelectionTable(props: FileSelectionTableProps):
    RPureComponent<FileSelectionTableProps, FileSelectionTableState>(props)
{
    override fun FileSelectionTableState.init(props: FileSelectionTableProps) {
        rangeAnchor = null
    }


    override fun componentDidUpdate(
        prevProps: FileSelectionTableProps,
        prevState: FileSelectionTableState,
        snapshot: Any
    ) {
        if (props.entries != prevProps.entries) {
            setState { rangeAnchor = null }
        }
    }


    private fun toggleChecked(index: Int, shift: Boolean) {
        val next = FileBrowserSelection.toggle(
            props.entries.map { it.location }, props.checked, index, state.rangeAnchor, shift)
        setState { rangeAnchor = index }
        props.onCheckedChanged(next)
    }


    override fun ChildrenBuilder.render() {
        div {
            css {
                fileTableScrollFrame(20.em)
                marginTop = 0.5.em
            }
            table {
                css { fileTableGrid() }
                renderHeader()
                tbody {
                    css { cursor = Cursor.default }
                    for ((index, entry) in props.entries.withIndex()) {
                        renderRow(entry, index)
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader() {
        val locations = props.entries.map { it.location }
        val allChecked = locations.isNotEmpty() && locations.all { it in props.checked }
        val anyChecked = locations.any { it in props.checked }

        thead {
            tr {
                fileTableHeaderCell {
                    Checkbox {
                        disableRipple = true
                        disabled = locations.isEmpty()
                        checked = allChecked
                        indeterminate = anyChecked && ! allChecked
                        asDynamic().inputProps = unsafeJso<Any> {
                            asDynamic()["aria-label"] =
                                if (allChecked) "Clear selection" else "Select all selected files"
                        }
                        onChange = { _, _ ->
                            props.onCheckedChanged(if (allChecked) emptySet() else locations.toSet())
                        }
                    }
                }
                fileTableHeaderCell { +"#" }
                fileTableHeaderCell { +"File" }
                if (props.showDetails && props.perEntryFormat) {
                    fileTableHeaderCell { +"Format" }
                    fileTableHeaderCell { +"Encoding" }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderRow(entry: FileSelectionEntry, index: Int) {
        val checked = entry.location in props.checked

        tr {
            key = Key(entry.location.asString())
            css {
                cursor = Cursor.pointer
                if (checked) {
                    backgroundColor = FileTableColors.checkedRow
                }
                hover {
                    backgroundColor =
                        if (checked) FileTableColors.checkedHoverRow
                        else FileTableColors.hoverRow
                }
            }
            onClick = { event ->
                val dynamicEvent: dynamic = event
                toggleChecked(index, dynamicEvent.shiftKey as Boolean)
            }

            td {
                Checkbox {
                    this.checked = checked
                    asDynamic().inputProps = unsafeJso<Any> {
                        asDynamic()["aria-label"] = "Select ${entry.location.fileName()}"
                    }
                    onClick = { it.stopPropagation() }
                    onChange = { _, _ -> toggleChecked(index, false) }
                }
            }

            td {
                css { paddingLeft = 0.5.em; paddingRight = 0.5.em; color = NamedColor.gray }
                +"${index + 1}"
            }

            td {
                css { width = 100.pct; paddingLeft = 0.5.em }
                +entry.location.fileName()

                // The full path is what disambiguates same-named files gathered from different folders, but it is
                // also the longest thing in the table - so it lives under Details, exactly as it does in Report.
                if (props.showDetails) {
                    div {
                        css { fontFamily = FontFamily.monospace; fontSize = 0.85.em; color = NamedColor.gray }
                        +entry.location.asString()
                    }
                }
            }

            if (props.showDetails && props.perEntryFormat) {
                val format = entry.format?.asString().orEmpty()
                val encoding = entry.encoding?.asString().orEmpty()

                td { entrySelect("Format", DataFormatOptions.formats(props.formatCatalog, format), format) {
                    props.onFormatChanged(index, it)
                } }
                td { entrySelect("Encoding", DataFormatOptions.encodings(props.formatCatalog, encoding), encoding) {
                    props.onEncodingChanged(index, it)
                } }
            }
        }
    }


    // A select rather than a text field: neither a format coordinate nor a charset name is guessable, and a
    // typo in either surfaces only as a failed run. The wrapping div stops a click inside the dropdown from
    // reaching the row (which would toggle the row's checkbox mid-choice).
    private fun ChildrenBuilder.entrySelect(
        label: String,
        options: Array<SelectOption>,
        value: String,
        onValue: (String) -> Unit
    ) {
        div {
            css { width = 11.em; paddingTop = 0.25.em; paddingBottom = 0.25.em }
            onClick = { it.stopPropagation() }

            muiAutocompleteField(
                label = label,
                options = options,
                selectedOption = options.find { it.value == value },
                onSelect = { onValue(it.value) },
                disableClearable = true)
        }
    }
}
