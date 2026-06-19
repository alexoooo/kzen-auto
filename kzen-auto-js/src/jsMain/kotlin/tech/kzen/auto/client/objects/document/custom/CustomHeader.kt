package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.material.Tooltip
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.model.CustomStore
import tech.kzen.auto.client.objects.document.custom.model.CustomStoreKey
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import web.cssom.NamedColor
import web.cssom.Padding
import web.cssom.em
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface CustomHeaderState: State {
    var viewMode: DocumentViewMode?
    var editorModified: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomHeader(
    props: Props
):
    RPureComponent<Props, CustomHeaderState>(props),
    CustomStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomHeaderState.init(props: Props) {
        viewMode = null
        editorModified = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    // The body's store, shared via the per-document bridge (provided by CustomController in render).
    private fun store(): CustomStore? =
        contextValue<DocumentBridge?>()?.lookup(CustomStoreKey)


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store()?.observe(this)
    }


    override fun componentWillUnmount() {
        store()?.unobserve(this)
    }


    override fun onCustomState(customState: CustomState) {
        setState {
            viewMode = customState.viewMode
            editorModified = customState.editorModified
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onModeChange(viewMode: DocumentViewMode) {
        if (viewMode == DocumentViewMode.View && state.editorModified) {
            return
        }
        store()?.setViewMode(viewMode)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val viewMode = state.viewMode
            ?: return
        val editorModified = state.editorModified

        div {
            css {
                padding = Padding(0.5.em, 1.em)
            }

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
    }


    private fun ChildrenBuilder.renderViewButton(editorModified: Boolean) {
        val button: ChildrenBuilder.() -> Unit = {
            ToggleButton {
                value = DocumentViewMode.View.name
                size = Size.medium
                this.disabled = editorModified

                css {
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
