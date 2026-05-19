package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.material.Tooltip
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomHeaderState: State {
    var viewMode: CustomViewMode
    var editorModified: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomHeader(
    props: Props
):
    RPureComponent<Props, CustomHeaderState>(props),
    CustomGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomHeaderState.init(props: Props) {
        viewMode = CustomViewMode.View
        editorModified = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        CustomGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        CustomGlobal.unobserve(this)
    }


    override fun onCustomState(state: CustomState) {
        setState {
            viewMode = state.viewMode
            editorModified = state.editorModified
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onModeChange(viewMode: CustomViewMode) {
        if (viewMode == CustomViewMode.View && state.editorModified) {
            return
        }
        CustomGlobal.setViewMode(viewMode)
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
                    (v as? String)?.let { onModeChange(CustomViewMode.valueOf(it)) }
                }

                renderViewButton()

                ToggleButton {
                    value = CustomViewMode.Raw.name
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


    private fun ChildrenBuilder.renderViewButton() {
        val disabled = state.editorModified

        val button: ChildrenBuilder.() -> Unit = {
            ToggleButton {
                value = CustomViewMode.View.name
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
