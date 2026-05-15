package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.material.Tooltip
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomDocumentHeaderState: State {
    var viewMode: CustomDocumentViewMode
    var editorModified: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomDocumentHeader(
    props: Props
):
    RPureComponent<Props, CustomDocumentHeaderState>(props),
    CustomDocumentGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomDocumentHeaderState.init(props: Props) {
        viewMode = CustomDocumentViewMode.View
        editorModified = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        CustomDocumentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        CustomDocumentGlobal.unobserve(this)
    }


    override fun onCustomDocumentState(state: CustomDocumentState) {
        setState {
            viewMode = state.viewMode
            editorModified = state.editorModified
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onModeChange(viewMode: CustomDocumentViewMode) {
        if (viewMode == CustomDocumentViewMode.View && state.editorModified) {
            return
        }
        CustomDocumentGlobal.setViewMode(viewMode)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                padding = Padding(0.5.em, 1.em)
            }

            ToggleButtonGroup {
                value = state.viewMode.name
                exclusive = true

                asDynamic()["onChange"] = { _, v ->
                    val selected = v as? String
                    if (selected != null) {
                        onModeChange(CustomDocumentViewMode.valueOf(selected))
                    }
                }

                renderViewButton()

                ToggleButton {
                    value = CustomDocumentViewMode.Raw.name
                    size = Size.medium

                    css {
                        height = 34.px
                        color = NamedColor.black
                        borderWidth = 2.px
                    }

                    +"Raw"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderViewButton() {
        val disabled = state.editorModified

        val button: ChildrenBuilder.() -> Unit = {
            ToggleButton {
                value = CustomDocumentViewMode.View.name
                size = Size.medium
                this.disabled = disabled

                css {
                    height = 34.px
                    color = NamedColor.black
                    borderWidth = 2.px
                }

                +"View"
            }
        }

        if (disabled) {
            Tooltip {
                title = react.ReactNode("Save or discard Raw changes to switch to View")

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
