package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.ToggleButton
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.persistentMapOf
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ResultSignatureEditorProps: Props {
    var objectLocation: ObjectLocation

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface ResultSignatureEditorState: State {
    // The declared `main` result type, or null when the Script is void (no `results.main`).
    var className: String?
    var nullable: Boolean
    // Generic type arguments are preserved across edits but not yet editable here (mirrors LogicSignatureEditor).
    var generics: ListAttributeNotation?

    // The type picker is expanded (vs the collapsed reader, or the add control when void).
    var editing: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a Script's result signature: the `main` component of the `results` map (component name -> a
 * TypeMetadata map). Unlike a parameter, the result is NOT a live object — it is plain data on the main
 * Script object — so this is a single optional type picker (no rows, no rename, no reorder). Floated at the
 * top-right of the script area, stacked beneath the Parameters control. Absent/empty => void Script;
 * only the `main` result is wired today (the map shape leaves room for more named results).
 */
class ResultSignatureEditor:
    RPureComponent<ResultSignatureEditorProps, ResultSignatureEditorState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val classKey = "class"
        private const val genericsKey = "generics"
        private const val nullableKey = "nullable"
        private const val mainResultKey = "main"
        private const val defaultClassName = "kotlin.Any"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ResultSignatureEditorState.init(props: ResultSignatureEditorProps) {
        className = null
        nullable = false
        generics = null
        editing = false
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphDefinitionAttempt.graphStructure.graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val resultsNotation = graphNotation.firstAttribute(
            props.objectLocation, ScriptConventions.resultsAttributePath) as? MapAttributeNotation
        val typeNotation = resultsNotation?.get(mainResultKey) as? MapAttributeNotation

        val newClassName = typeNotation?.get(classKey)?.asString()
        val newNullable = typeNotation?.get(nullableKey)?.asString()?.toBoolean() ?: false
        val newGenerics = typeNotation?.get(genericsKey) as? ListAttributeNotation

        // Guard against the fresh notation objects each fire produces so RPureComponent's shallow state
        // comparison doesn't re-render on unchanged content.
        if (newClassName == state.className && newNullable == state.nullable && newGenerics == state.generics) {
            return
        }

        setState {
            className = newClassName
            nullable = newNullable
            generics = newGenerics
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddResult() {
        setState { editing = true }
        // type/nullable apply live (state updates flow back via onClientState); seed with Any.
        writeMainResult(defaultClassName, false, null)
    }


    private fun onTypeChange(className: String, nullable: Boolean) {
        // Read prior generics outside the command (state read inside setState is write-only — see wrap/React.kt).
        writeMainResult(className, nullable, state.generics)
    }


    private fun onRemoveResult() {
        setState { editing = false }
        // An empty `results` map is a void Script.
        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                ScriptConventions.resultsAttributeName,
                MapAttributeNotation.empty))
        }
    }


    // Replace the whole `results` map with a single `main` entry (only main is supported for now) — robust
    // whether or not `results` already exists.
    private fun writeMainResult(className: String, nullable: Boolean, generics: ListAttributeNotation?) {
        val typeNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(classKey) to ScalarAttributeNotation(className),
            AttributeSegment.ofKey(genericsKey) to (generics ?: ListAttributeNotation.empty),
            AttributeSegment.ofKey(nullableKey) to ScalarAttributeNotation(nullable.toString())))

        val resultsNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(mainResultKey) to typeNotation))

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                ScriptConventions.resultsAttributeName,
                resultsNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                // Floated at the top-right of the script area, stacked directly beneath the Parameters control
                // (absolute, anchored to ScriptController's relative container) so it never adds a row of its
                // own. The top offset clears the Parameters control row; right-anchoring (like Parameters)
                // keeps it clear of the parameter list/editor, which flow in the left-hand dependency column.
                position = Position.absolute
                top = 2.75.em
                right = 0.5.em
                display = Display.flex
                alignItems = AlignItems.center
                // above the step cards (which are positioned but auto z-index) so it stays clickable.
                zIndex = integer(2)
            }

            val className = state.className
            when {
                className == null -> renderAddControl()
                state.editing -> renderEditor(className)
                else -> renderReader(className)
            }
        }
    }


    private fun ChildrenBuilder.renderAddControl() {
        span {
            css {
                fontSize = 0.8.em
                color = Color("gray")
                marginRight = 0.25.em
            }
            +"Result"
        }

        IconButton {
            title = "Add result"
            size = Size.small
            onClick = { onAddResult() }
            icon("material-symbols:add-circle-outline") {}
        }
    }


    private fun ChildrenBuilder.renderReader(className: String) {
        // The `Result: Type` reads as one clickable edit affordance: hovering tints it and fades in the pencil.
        div {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                borderRadius = 4.px
                paddingLeft = 0.25.em
                paddingRight = 0.25.em
                cursor = Cursor.pointer
                transition = "background-color 120ms ease-out".unsafeCast<Transition>()

                "&:hover" {
                    backgroundColor = Color("rgba(0, 0, 0, 0.06)")
                }
                "&:hover [data-edit-button]" {
                    opacity = number(1.0)
                }
            }

            onClick = { setState { editing = true } }

            span {
                css { color = Color("gray") }
                +"Result: "
            }
            span {
                css { fontWeight = FontWeight.bold }
                +typeLabel(className, state.nullable)
            }

            div {
                asDynamic()["data-edit-button"] = ""
                css {
                    display = Display.inlineFlex
                    alignItems = AlignItems.center
                    marginLeft = 0.25.em
                    opacity = number(0.0)
                    transition = "opacity 120ms ease-out".unsafeCast<Transition>()
                }

                IconButton {
                    title = "Edit result"
                    size = Size.small
                    icon("material-symbols:edit") {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditor(className: String) {
        span {
            css {
                fontSize = 0.8.em
                color = Color("gray")
                marginRight = 0.5.em
            }
            +"Result"
        }

        span {
            css {
                display = Display.inlineBlock
                width = 8.em
                marginRight = 0.5.em
            }

            val typeOptions = LogicTypeOptions.classOptions
                .map { (value, simpleLabel) ->
                    val option: SelectOption = unsafeJso {
                        this.value = value
                        this.label = simpleLabel
                    }
                    option
                }
                .toTypedArray()

            muiAutocompleteField(
                label = "Type",
                options = typeOptions,
                selectedOption = typeOptions.find { it.value == className },
                onSelect = { onTypeChange(it.value, state.nullable) },
                disableClearable = true,
                // Once the dropdown is closed, Enter/Escape collapse the editor (the first Enter/Escape still
                // picks/closes the list). Type/nullable apply live, so there is nothing to revert — both keys
                // simply close, matching the "Done" button.
                onClosedKeyDown = { event ->
                    ClientInputUtils.handleEnterAndEscape(
                        event,
                        { setState { editing = false } },
                        { setState { editing = false } })
                })
        }

        // Nullable as a compact toggle (`?`), matching the parameter editor.
        ToggleButton {
            value = "nullable"
            selected = state.nullable
            size = Size.small
            sx {
                height = 28.px
                marginRight = 0.5.em
            }
            title =
                if (state.nullable) {
                    "Nullable (click to require non-null)"
                }
                else {
                    "Allow null"
                }
            onChange = { _, _ -> onTypeChange(className, !state.nullable) }
            icon("material-symbols:question-mark") {}
        }

        IconButton {
            title = "Remove result (void)"
            size = Size.small
            onClick = { onRemoveResult() }
            icon("material-symbols:delete") {}
        }

        IconButton {
            title = "Done"
            size = Size.small
            onClick = { setState { editing = false } }
            icon("material-symbols:check") {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeLabel(className: String, nullable: Boolean): String {
        val simple = LogicTypeOptions.simpleLabelByClassName[className]
            ?: className.substringAfterLast('.')
        return if (nullable) "$simple?" else simple
    }
}
