package tech.kzen.auto.client.wrap.select

import emotion.react.css
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
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.NamedColor
import web.cssom.Overflow
import web.cssom.TextOverflow
import web.cssom.WhiteSpace
import web.cssom.em
import web.cssom.px


// Spreads MUI's AutocompleteRenderInputParams onto the TextField props (slotProps / id / disabled /
// ...), which renderInput must forward for the combobox input to be wired up. Kept as a NON-inline
// top-level function so the js(...) Object.assign call is legal (js() is rejected inside inline lambdas).
private fun objectAssign(target: Any, source: Any) {
    js("Object.assign(target, source)")
}


private fun ChildrenBuilder.optionRow(option: SelectOption) {
    val detail = option.detail
    if (detail == null) {
        +option.label
        return
    }

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            overflow = Overflow.hidden
        }

        +option.label

        div {
            option.detailTitle?.let { this.title = it }
            css {
                fontSize = 0.8.em
                color = Color("rgba(0, 0, 0, 0.55)")
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
            }
            +detail
        }
    }
}


// A labelled MUI Autocomplete select/filter field — the single, consistently-labelled select used across the
// client (the ergonomic replacement for a bare react-select). Options carry their identity + display text as
// a SelectOption (value/label); selection identity and equality are by `value`.
//
// `Autocomplete` ships as `FC<AutocompleteProps<*>>` (star-projected), so it is cast to the concrete
// FC<AutocompleteProps<SelectOption>> before invocation — the standard kotlin-wrappers idiom for generic
// components.
//
// `disablePortal` keeps the listbox inside the field's own DOM subtree (default false portals it to the
// body to avoid clipping); a caller that wraps the field in a ClickAwayListener sets it true so option /
// scroll clicks count as "inside".
//
// `opaqueBackground` fills the otherwise-transparent outlined field (and the floating label's overhang)
// with white — for fields that float over arbitrary content (e.g. the script canvas) so nothing ghosts
// through.
//
// `open` puts the listbox's open state under caller control; null (the default) leaves MUI's own
// uncontrolled toggle in charge. `onOpen` and `onClose` report the transitions the USER initiates — for
// selects that lazily load their options on first open, and for those whose open state doubles as some
// other mode. Driving the `open` prop from state does not itself re-fire them. `onClose` carries MUI's
// reason (toggleInput / blur / selectOption / escape); the reason is the only signal a caller gets for
// Escape, since MUI preventDefault+stopPropagation's that keydown so no window-level listener sees it.
//
// `onClosedKeyDown` lets a caller treat the field like a plain text input for Enter/Escape WHEN THE DROPDOWN
// IS CLOSED. MUI's Autocomplete consumes Enter/Escape only while its listbox is open (Enter selects the
// highlighted option, Escape closes the listbox); once closed those keys are no-ops it never forwards. This
// hook fires only in that closed state (read off the combobox input's `aria-expanded`), so the first
// Enter/Escape still picks/closes as usual and a *subsequent* Enter/Escape can commit/cancel a surrounding
// editor — matching plain TextField fields beside the select. MUI invokes our onKeyDown before its own switch
// (see useAutocomplete handleKeyDown), so the open state read here is the one settled by the previous
// keypress. With the listbox pinned open (`open = true`) it never fires — use `onClose` there instead.
fun ChildrenBuilder.muiAutocompleteField(
    label: String,
    options: Array<SelectOption>,
    selectedOption: SelectOption?,
    onSelect: (SelectOption) -> Unit,
    disableClearable: Boolean = false,
    autoFocus: Boolean = false,
    autoHighlight: Boolean = true,
    openOnFocus: Boolean = false,
    disabled: Boolean = false,
    disablePortal: Boolean = false,
    error: Boolean = false,
    open: Boolean? = null,
    opaqueBackground: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onClose: ((AutocompleteCloseReason) -> Unit)? = null,
    onClosedKeyDown: ((react.dom.events.KeyboardEvent<*>) -> Unit)? = null
) {
    val component = Autocomplete.unsafeCast<FC<AutocompleteProps<SelectOption>>>()
    component {
        this.options = options.unsafeCast<ReadonlyArray<SelectOption>>()
        this.value = selectedOption
        this.getOptionLabel = { it.label }
        this.isOptionEqualToValue = { a, b -> a.value == b.value }
        if (options.any { it.group != null }) {
            this.groupBy = { it.group ?: "" }
        }
        this.disableClearable = disableClearable
        // Auto-highlight the first (top) filtered option as the user types so Enter selects it without first
        // arrow-down'ing or hovering. Default true app-wide (MUI's own default is false — Enter does nothing
        // until something is highlighted); pass autoHighlight = false to opt a field out.
        this.autoHighlight = autoHighlight
        this.openOnFocus = openOnFocus
        this.disabled = disabled
        this.disablePortal = disablePortal
        this.fullWidth = true

        if (onOpen != null) {
            this.onOpen = { onOpen() }
        }

        // Only assign when the caller opted in: leaving `open` unset is what keeps MUI's own uncontrolled
        // toggle in charge, and undefined is not the same as false to useControlled.
        if (open != null) {
            this.open = open
        }

        if (onClose != null) {
            this.onClose = { _, reason -> onClose(reason) }
        }

        // onChange is (event, value: Any, reason, details) — value is the picked option (non-null here
        // since the field is never cleared while editing); narrow it back to SelectOption.
        this.onChange = { _, picked, _, _ ->
            onSelect(picked.unsafeCast<SelectOption>())
        }

        // MUI doesn't destructure onKeyDown, so a prop set here flows into getRootProps(other) and is invoked
        // before MUI's own Enter/Escape handling (see the param doc above). Forward to the caller only when
        // the dropdown is closed (aria-expanded on the combobox input), so we never hijack the keypress that
        // selects an option or closes the listbox. Set via asDynamic() to avoid depending on the typed prop
        // surface (same idiom as `value` in the multi-field below).
        if (onClosedKeyDown != null) {
            this.asDynamic().onKeyDown = { event: react.dom.events.KeyboardEvent<*> ->
                // Read aria-expanded off the event's combobox input dynamically — robust to whichever DOM
                // type the wrappers give `target`, and the same asDynamic idiom used for `value` below.
                val expanded = event.target.asDynamic().getAttribute("aria-expanded") == "true"
                if (!expanded) {
                    onClosedKeyDown(event)
                }
            }
        }

        // Two-line option rows, assigned only when some option actually carries a detail so every other field
        // keeps MUI's own single-line row verbatim. Set through asDynamic() to stay off the typed prop's
        // arity (MUI passes props/option/state/ownerState; a Kotlin lambda taking the first two is called
        // with the rest ignored). The props argument MUST be spread onto the <li> — it carries the listbox's
        // click, highlight and aria wiring, and the row's React key.
        if (options.any { it.detail != null }) {
            this.asDynamic().renderOption = { optionProps: Any, option: SelectOption ->
                ReactHTML.li.create {
                    objectAssign(this, optionProps)
                    optionRow(option)
                }
            }
        }

        this.renderInput = { params ->
            TextField.create {
                objectAssign(this, params)
                this.label = ReactNode(label)
                this.size = Size.small
                this.error = error
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


// Multi-select sibling of muiAutocompleteField: an inline-labelled MUI Autocomplete with `multiple = true`,
// rendering the selection as removable chips. `onChange` receives the full new selection array (MUI delivers
// the complete set on every add/remove), so callers diff against the prior set as needed.
fun ChildrenBuilder.muiAutocompleteMultiField(
    label: String,
    options: Array<SelectOption>,
    selectedOptions: Array<SelectOption>,
    onChange: (Array<SelectOption>) -> Unit,
    disabled: Boolean = false
) {
    val component = Autocomplete.unsafeCast<FC<AutocompleteProps<SelectOption>>>()
    component {
        this.multiple = true
        this.options = options.unsafeCast<ReadonlyArray<SelectOption>>()
        // `value` is typed for the single-select projection above; for multiple it is the selection array.
        this.asDynamic().value = selectedOptions
        this.getOptionLabel = { it.label }
        this.isOptionEqualToValue = { a, b -> a.value == b.value }
        if (options.any { it.group != null }) {
            this.groupBy = { it.group ?: "" }
        }
        this.disabled = disabled
        this.fullWidth = true

        this.onChange = { _, picked, _, _ ->
            onChange(picked.unsafeCast<Array<SelectOption>>())
        }

        this.renderInput = { params ->
            TextField.create {
                objectAssign(this, params)
                this.label = ReactNode(label)
                this.size = Size.small
            }
        }
    }
}
