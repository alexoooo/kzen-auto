package tech.kzen.auto.client.objects.document.script

import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.material.Tooltip
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.script.model.ScriptGlobal
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import web.cssom.NamedColor
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptViewModeToggleState: State {
    var viewMode: DocumentViewMode?
    var editorModified: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// View / Raw toggle for the Script header. Mounted alongside the ribbon in ScriptController.header()
// (a sibling subtree to the body), so it reaches the store via ScriptGlobal rather than context.
// Mirrors the toggle portion of CustomHeader.
@Suppress("unused")
class ScriptViewModeToggle(
    props: Props
):
    RPureComponent<Props, ScriptViewModeToggleState>(props),
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptViewModeToggleState.init(props: Props) {
        viewMode = null
        editorModified = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ScriptGlobal.get().observe(this)
    }


    override fun componentWillUnmount() {
        ScriptGlobal.get().unobserve(this)
    }


    override fun onScriptState(scriptState: ScriptState) {
        setState {
            viewMode = scriptState.viewMode
            editorModified = scriptState.editorModified
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onModeChange(viewMode: DocumentViewMode) {
        if (viewMode == DocumentViewMode.View && state.editorModified) {
            return
        }
        ScriptGlobal.get().setViewMode(viewMode)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val viewMode = state.viewMode
            ?: return
        val editorModified = state.editorModified

        ToggleButtonGroup {
            value = viewMode.name
            exclusive = true

            asDynamic()["onChange"] = { _, v ->
                (v as? String)?.let { onModeChange(DocumentViewMode.valueOf(it)) }
            }

            renderViewButton(editorModified)

            ToggleButton {
                value = DocumentViewMode.Raw.name
                size = Size.medium

                sx {
                    height = 34.px
                    color = NamedColor.black
                    borderWidth = 2.px
                }

                +"Raw"
            }
        }
    }


    private fun ChildrenBuilder.renderViewButton(editorModified: Boolean) {
        val button: ChildrenBuilder.() -> Unit = {
            ToggleButton {
                value = DocumentViewMode.View.name
                size = Size.medium
                this.disabled = editorModified

                sx {
                    height = 34.px
                    color = NamedColor.black
                    borderWidth = 2.px
                }

                +"View"
            }
        }

        if (editorModified) {
            Tooltip {
                title = ReactNode("Save or discard Raw changes to switch to View")

                span {
                    button()
                }
            }
        }
        else {
            button()
        }
    }
}
