package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.StageFloatStack
import tech.kzen.auto.client.objects.document.stageFloatRow
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.logic.TypeMetadataDefiner
import tech.kzen.auto.common.paradigm.logic.LogicConventions
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
external interface ResultSignatureEditorProps: ObjectScopedProps {
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
 * Edits a Logic document's result signature (Script and Job): the `main` component of the `results` map
 * (component name -> a TypeMetadata map). Unlike a parameter, the result is NOT a live object — it is plain
 * data on the main object — so this is a single optional type picker (no rows, no rename, no reorder).
 * Floated at the top-right of the stage, stacked beneath the Parameters control. Absent/empty => void;
 * only the `main` result is wired today (the map shape leaves room for more named results).
 */
class ResultSignatureEditor:
    ObjectScopedComponent<ResultSignatureEditorProps, ResultSignatureEditorState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphDefinitionAttempt.graphStructure.graphNotation

        val resultsNotation = graphNotation.firstAttribute(
            props.objectLocation, LogicConventions.resultsAttributePath) as? MapAttributeNotation
        val typeNotation = resultsNotation?.get(mainResultKey) as? MapAttributeNotation

        val newClassName = typeNotation?.get(TypeMetadataDefiner.classKey)?.asString()
        val newNullable = typeNotation?.get(TypeMetadataDefiner.nullableKey)?.asString()?.toBoolean() ?: false
        val newGenerics = typeNotation?.get(TypeMetadataDefiner.genericsKey) as? ListAttributeNotation

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
                LogicConventions.resultsAttributeName,
                MapAttributeNotation.empty))
        }
    }


    // Replace the whole `results` map with a single `main` entry (only main is supported for now) — robust
    // whether or not `results` already exists.
    private fun writeMainResult(className: String, nullable: Boolean, generics: ListAttributeNotation?) {
        val typeNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(TypeMetadataDefiner.classKey) to ScalarAttributeNotation(className),
            AttributeSegment.ofKey(TypeMetadataDefiner.genericsKey) to
                    (generics ?: ListAttributeNotation.empty),
            AttributeSegment.ofKey(TypeMetadataDefiner.nullableKey) to
                    ScalarAttributeNotation(nullable.toString())))

        val resultsNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(mainResultKey) to typeNotation))

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                LogicConventions.resultsAttributeName,
                resultsNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                // Right-anchored (like Parameters above it) keeps it clear of the parameter list/editor, which
                // flow in the left-hand dependency column.
                stageFloatRow(StageFloatStack.resultRow)
                display = Display.flex
                alignItems = AlignItems.center
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
                +LogicTypeOptions.simpleLabel(className, state.nullable)
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

        logicTypePicker(
            className = className,
            nullable = state.nullable,
            onTypeChange = ::onTypeChange,
            // Once the dropdown is closed, Enter/Escape collapse the editor (the first Enter/Escape still
            // picks/closes the list). Type/nullable apply live, so there is nothing to revert — both keys
            // simply close, matching the "Done" button.
            onClosedKeyDown = { event ->
                ClientInputUtils.handleEnterAndEscape(
                    event,
                    { setState { editing = false } },
                    { setState { editing = false } })
            })

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
}
