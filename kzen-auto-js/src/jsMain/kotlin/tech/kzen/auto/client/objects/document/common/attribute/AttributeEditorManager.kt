package tech.kzen.auto.client.objects.document.common.attribute

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.Color
import web.cssom.Display
import web.cssom.LineStyle
import web.cssom.em
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
// NB: the manager's own dispatch contract, not a subtype of AttributeEditorProps - hosts set only the two
// addressing fields, and the Wrapper supplies the rest. Inheriting the editor contract dragged in a
// mirroredGraphStore that nothing ever set.
external interface AttributeEditorManagerProps: ObjectScopedProps {
    var attributeName: AttributeName

    var attributeEditors: List<AttributeEditor>
}


external interface AttributeEditorManagerState: State {
    var attributeEditorName: ObjectName?
    var attributeEditor: AttributeEditor?
    var missingEditorName: ObjectName?

    // This attribute's definition-failure message (or null), so the field can highlight itself in place
    // rather than the document showing a top-level error banner.
    var attributeError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class AttributeEditorManager(
    props: AttributeEditorManagerProps
):
    ObjectScopedComponent<AttributeEditorManagerProps, AttributeEditorManagerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Matches the validation-error accent on step cards (ScriptStepDisplayDefault.validationErrorColour) —
        // a red-orange, distinct from the darker run-failure red.
        private val definitionErrorColour = Color("#d84315")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val attributeEditors: List<AttributeEditor>,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        ReactWrapper<AttributeEditorManagerProps>
    {
        override fun ChildrenBuilder.child(block: AttributeEditorManagerProps.() -> Unit) {
            AttributeEditorManager::class.react {
                this.attributeEditors = this@Wrapper.attributeEditors
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        // Resolve the editor once — an attribute's `editor:` metadata is stable across store updates.
        if (state.attributeEditor == null) {
            val editorWrapperName = AttributeWrapperLookup.wrapperName(
                clientState.graphStructure(),
                props.objectLocation,
                props.attributeName,
                AttributeWrapperLookup.editorAttributePath
            ) ?: DefaultAttributeEditor.wrapperName

            val attributeEditor =
                props.attributeEditors.find { it.name() == editorWrapperName }
            val fallbackEditor =
                if (attributeEditor == null && editorWrapperName != DefaultAttributeEditor.wrapperName) {
                    props.attributeEditors.find { it.name() == DefaultAttributeEditor.wrapperName }
                }
                else {
                    null
                }

            setState {
                this.attributeEditorName = editorWrapperName
                this.attributeEditor = attributeEditor ?: fallbackEditor
                this.missingEditorName = if (fallbackEditor == null) null else editorWrapperName
            }
        }

        // Track this attribute's definition failure (if any). Value-guarded so an unrelated store update
        // — the common case — doesn't re-render the editor.
        val attributeError = clientState
            .graphDefinitionAttempt
            .failures.map[props.objectLocation]
            ?.attributeErrors?.get(props.attributeName)

        if (attributeError != state.attributeError) {
            setState {
                this.attributeError = attributeError
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val editorWrapper = state.attributeEditor
        if (editorWrapper == null) {
            +"[Attribute editor not found: ${state.attributeEditorName}]"
            return
        }

        val attributeError = state.attributeError

        // A definition failure for this attribute frames the editor in place — a left red-orange accent plus the
        // message beneath it — so the fix happens at the field (replacing the old document-wide banner).
        //
        // NB: the wrapper `div` and the editor inside it are emitted UNCONDITIONALLY; only the accent css and the
        //     trailing message toggle with the error. If the editor were a bare child when clean and a nested
        //     child when erroring, clearing the error (the moment the user picks a valid value) would move it in
        //     the tree and REMOUNT it — dropping focus / the open dropdown mid-fix. The message div is the LAST
        //     child, so adding/removing it never shifts the editor's index.
        div {
            css {
                if (attributeError != null) {
                    borderLeftWidth = 3.px
                    borderLeftStyle = LineStyle.solid
                    borderLeftColor = definitionErrorColour
                    paddingLeft = 0.5.em
                }
                else {
                    // Zero layout footprint when clean: the editor lays out exactly as an unwrapped child would,
                    // while this persistent wrapper element keeps the editor's tree position stable.
                    display = Display.contents
                }
            }

            renderEditor(this, editorWrapper)

            if (attributeError != null) {
                div {
                    css {
                        marginTop = 0.25.em
                        color = definitionErrorColour
                        fontSize = 0.8.em
                    }
                    +attributeError
                }
            }

            state.missingEditorName?.let { missingEditorName ->
                div {
                    css {
                        marginTop = 0.25.em
                        color = Color("#9a6700")
                        fontSize = 0.8.em
                    }
                    +"Editor unavailable: $missingEditorName; using the default editor."
                }
            }
        }
    }


    private fun renderEditor(childrenBuilder: ChildrenBuilder, editorWrapper: AttributeEditor) {
        editorWrapper.child(childrenBuilder) {
            objectLocation = props.objectLocation
            attributeName = props.attributeName
        }
    }
}
