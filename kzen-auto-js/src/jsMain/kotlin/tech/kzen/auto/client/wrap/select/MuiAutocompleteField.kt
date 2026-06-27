package tech.kzen.auto.client.wrap.select

import js.array.ReadonlyArray
import mui.base.AutocompleteCloseReason
import mui.material.Autocomplete
import mui.material.AutocompleteProps
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.FC
import react.ReactNode
import react.create
import web.cssom.NamedColor
import web.cssom.px


// Spreads MUI's AutocompleteRenderInputParams onto the TextField props (InputProps / inputProps / id /
// ...), which renderInput must forward for the combobox input to be wired up. Kept as a NON-inline
// top-level function so the js(...) Object.assign call is legal (js() is rejected inside inline lambdas).
private fun objectAssign(target: Any, source: Any) {
    js("Object.assign(target, source)")
}


// A labelled MUI Autocomplete select/filter field — the ergonomic, consistently-labelled replacement for
// a bare react-select. The floating material `label` matches the look of the project's TextField editors
// (TextAttributeEditor etc.), and Autocomplete closes its listbox on click-away natively. Generic over the
// option type T (carry value + display label in T and expose them via optionLabel / optionsEqual).
//
// `Autocomplete` ships as `FC<AutocompleteProps<*>>` (star-projected), so it is cast to the concrete
// FC<AutocompleteProps<T>> before invocation — the standard kotlin-wrappers idiom for generic components.
//
// `disablePortal` keeps the listbox inside the field's own DOM subtree (default false portals it to the
// body to avoid clipping); a caller that wraps the field in a ClickAwayListener sets it true so option /
// scroll clicks count as "inside".
//
// `opaqueBackground` fills the otherwise-transparent outlined field (and the floating label's overhang)
// with white — for fields that float over arbitrary content (e.g. the script canvas) so nothing ghosts
// through. `onEscape` (meaningful only with `forceOpen`) is invoked when the user presses Escape: MUI's
// Autocomplete preventDefault+stopPropagation's the Escape keydown, so a window-level listener never sees
// it, and this onClose(escape) is the only signal a forced-open popover gets to cancel.
fun <T> ChildrenBuilder.muiAutocompleteField(
    label: String,
    options: Array<T>,
    selectedOption: T?,
    optionLabel: (T) -> String,
    optionsEqual: (T, T) -> Boolean,
    onSelect: (T) -> Unit,
    disableClearable: Boolean = false,
    autoFocus: Boolean = false,
    autoHighlight: Boolean = false,
    openOnFocus: Boolean = false,
    disabled: Boolean = false,
    disablePortal: Boolean = false,
    forceOpen: Boolean = false,
    opaqueBackground: Boolean = false,
    onEscape: (() -> Unit)? = null
) {
    val component = Autocomplete.unsafeCast<FC<AutocompleteProps<T>>>()
    component {
        this.options = options.unsafeCast<ReadonlyArray<T>>()
        this.value = selectedOption
        this.getOptionLabel = optionLabel
        this.isOptionEqualToValue = optionsEqual
        this.disableClearable = disableClearable
        // Auto-highlight the first (top) filtered option as the user types so Enter selects it without first
        // arrow-down'ing or hovering. MUI's default is false (Enter does nothing until something is
        // highlighted); opt-in per field.
        this.autoHighlight = autoHighlight
        this.openOnFocus = openOnFocus
        this.disabled = disabled
        this.disablePortal = disablePortal
        this.fullWidth = true

        // Pin the listbox open for the lifetime of the field (used by transient popovers that ARE the
        // dropdown). This removes the input-click toggle: clicking the already-focused input can't collapse
        // the list, and because the list is always shown a single click-away closes the popover (an outer
        // ClickAwayListener fires onClickAway). A selection unmounts the whole popover so it need never close
        // itself; the only close we honour is Escape -> onEscape (see the param doc above).
        if (forceOpen) {
            this.open = true
            this.onClose = { _, reason ->
                if (reason == AutocompleteCloseReason.escape) {
                    onEscape?.invoke()
                }
            }
        }

        // onChange is (event, value: Any, reason, details) — value is the picked option (non-null here
        // since the field is never cleared while editing); narrow it back to T.
        this.onChange = { _, picked, _, _ ->
            onSelect(picked.unsafeCast<T>())
        }

        this.renderInput = { params ->
            TextField.create {
                objectAssign(this, params)
                this.label = ReactNode(label)
                this.size = Size.small
                if (autoFocus) {
                    this.autoFocus = true
                }
                if (opaqueBackground) {
                    // White fill matching the outlined 4px corner, plus an opaque chip behind the floating
                    // label so its half that overhangs the field's top border doesn't ghost the content
                    // behind the field. The selector needs the leading `&` (codebase convention) to scope as
                    // a descendant of the field root; the small horizontal padding widens the chip to cover
                    // the outline's notch gap on either side of the label text.
                    sx {
                        backgroundColor = NamedColor.white
                        borderRadius = 4.px
                        "& .MuiInputLabel-root" {
                            backgroundColor = NamedColor.white
                            paddingLeft = 4.px
                            paddingRight = 4.px
                        }
                    }
                }
            }
        }
    }
}
