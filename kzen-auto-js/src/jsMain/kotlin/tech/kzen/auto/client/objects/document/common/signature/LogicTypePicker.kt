package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Size
import mui.material.ToggleButton
import mui.system.sx
import react.ChildrenBuilder
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import web.cssom.Display
import web.cssom.Length
import web.cssom.em
import web.cssom.px


// Built once: the option set is constant, so every picker shares one array rather than rebuilding it per render.
private val typeOptions: Array<SelectOption> = LogicTypeOptions.classOptions
    .map { (value, simpleLabel) ->
        val option: SelectOption = unsafeJso {
            this.value = value
            this.label = simpleLabel
        }
        option
    }
    .toTypedArray()


/**
 * The class + nullability control every editor of a `TypeMetadata` notation map shares — a Script's parameters
 * and result, and a Contexts document's declarations. Both halves apply LIVE: the pick and the toggle each ARE
 * the commit, so [onTypeChange] always carries the full pair rather than one changed half.
 *
 * [onClosedKeyDown] is what Enter/Escape mean once the dropdown is closed (see `muiAutocompleteField`) — commit,
 * cancel, or merely collapse, which is the surrounding editor's decision.
 *
 * [itemSpacing] is the gap to whatever follows; a caller whose row already spaces its children (a flex `gap`)
 * passes null.
 */
fun ChildrenBuilder.logicTypePicker(
    className: String,
    nullable: Boolean,
    onTypeChange: (className: String, nullable: Boolean) -> Unit,
    onClosedKeyDown: (KeyboardEvent<*>) -> Unit,
    itemSpacing: Length? = 0.5.em
) {
    span {
        css {
            display = Display.inlineBlock
            width = 8.em
            itemSpacing?.let { marginRight = it }
        }

        muiAutocompleteField(
            label = "Type",
            options = typeOptions,
            selectedOption = typeOptions.find { it.value == className },
            onSelect = { onTypeChange(it.value, nullable) },
            disableClearable = true,
            onClosedKeyDown = onClosedKeyDown)
    }

    // Nullable as a compact toggle (`?`) rather than a switch + text label — the pressed state IS the meaning,
    // and it reclaims horizontal room on a single editor row.
    ToggleButton {
        value = "nullable"
        selected = nullable
        size = Size.small
        sx {
            height = 28.px
            itemSpacing?.let { marginRight = it }
        }
        title =
            if (nullable) {
                "Nullable (click to require non-null)"
            }
            else {
                "Allow null"
            }
        onChange = { _, _ -> onTypeChange(className, !nullable) }
        icon("material-symbols:question-mark") {}
    }
}
