package tech.kzen.auto.client.objects.document.common.file.format

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.InputLabel
import mui.material.Size
import mui.material.Switch
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexWrap
import web.cssom.em
import web.cssom.px
import web.cssom.pct


external interface DelimitedFormatOverrideEditorState: State {
    var draft: DelimitedFormatOverrideDraft
}


class DelimitedFormatOverrideEditor(
    props: FormatOverrideEditorProps
): RPureComponent<FormatOverrideEditorProps, DelimitedFormatOverrideEditorState>(props) {
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ): FormatOverrideEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: FormatOverrideEditorProps.() -> Unit) {
            DelimitedFormatOverrideEditor::class.react(block)
        }
    }

    override fun DelimitedFormatOverrideEditorState.init(props: FormatOverrideEditorProps) {
        draft = DelimitedFormatOverrideDraft.of(
            props.editorState.part.resolvedRead.config as MapExecutionValue)
    }

    private fun edit(transform: (DelimitedFormatOverrideDraft) -> DelimitedFormatOverrideDraft) {
        setState { draft = transform(draft) }
    }

    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                gap = 0.75.em
                width = 100.pct
                marginTop = 0.5.em
            }

            div {
                css { width = 100.pct }
                span { +"Delimiter" }
                delimiterButton("Comma", ",")
                delimiterButton("Tab", "\t")
                delimiterButton("Semicolon", ";")
                delimiterButton("Pipe", "|")
            }

            div {
                span { +"Exact delimiter " }
                input {
                    value = state.draft.delimiter
                    maxLength = 1
                    disabled = props.applying
                    onChange = { event -> edit { it.copy(delimiter = event.currentTarget.value) } }
                }
            }

            InputLabel {
                +"First row is header"
                Switch {
                    checked = state.draft.firstRowHeader
                    disabled = props.applying
                    onChange = { event, _ ->
                        edit { it.copy(firstRowHeader = event.currentTarget.checked) }
                    }
                }
            }

            div {
                css { width = 16.em }
                val options = props.editorState.encodings.map { encoding ->
                    js.objects.unsafeJso<SelectOption> {
                        value = encoding
                        label = encoding
                    }
                }.toTypedArray()
                muiAutocompleteField(
                    label = "Encoding",
                    options = options,
                    selectedOption = options.find { it.value == state.draft.encoding },
                    onSelect = { selected -> edit { it.copy(encoding = selected.value) } },
                    disableClearable = true,
                    disabled = props.applying)
            }

            div {
                span { +"Leading lines to skip " }
                input {
                    value = state.draft.skipLeadingLines
                    disabled = props.applying
                    onChange = { event -> edit { it.copy(skipLeadingLines = event.currentTarget.value) } }
                }
            }

            div {
                span { +"Comment prefix (optional) " }
                input {
                    value = state.draft.commentPrefix
                    disabled = props.applying
                    onChange = { event -> edit { it.copy(commentPrefix = event.currentTarget.value) } }
                }
            }

            div {
                css {
                    width = 100.pct
                    color = Color("rgba(0, 0, 0, 0.65)")
                    fontSize = 0.85.em
                }
                +state.draft.headerExplanation()
                if (state.draft.commentPrefix.isNotEmpty()) {
                    +" Records beginning with the exact comment prefix are ignored."
                }
            }

            state.draft.error?.let { message ->
                div {
                    css {
                        width = 100.pct
                        color = Color("#c62828")
                    }
                    +message
                }
            }
            props.applyError?.let { message ->
                div {
                    css {
                        width = 100.pct
                        color = Color("#c62828")
                    }
                    +message
                }
            }

            Button {
                variant = ButtonVariant.contained
                size = Size.small
                disabled = props.applying || state.draft.error != null
                onClick = { props.onApply(state.draft.overrides()) }
                +if (props.applying) "Applying…" else "Apply to this file"
            }
        }
    }

    private fun ChildrenBuilder.delimiterButton(label: String, delimiter: String) {
        Button {
            variant = if (state.draft.delimiter == delimiter) ButtonVariant.contained else ButtonVariant.outlined
            size = Size.small
            disabled = props.applying
            sx {
                marginLeft = 0.4.em
                minWidth = 0.px
            }
            onClick = { edit { it.copy(delimiter = delimiter) } }
            +label
        }
    }
}
