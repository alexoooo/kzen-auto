package tech.kzen.auto.client.objects.document.common.edit

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.textarea
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface YamlEditorProps: Props {
    var value: String
    var onChange: (String) -> Unit
    var onSave: (() -> Unit)?
    var error: String?
    var disabled: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class YamlEditor(
    props: YamlEditorProps
):
    RPureComponent<YamlEditorProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val gutterRef: RefObject<HTMLDivElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                border = Border(1.px, LineStyle.solid, Color("rgb(192, 192, 192)"))
                backgroundColor = Color("#fafafa")
                fontFamily = FontFamily.monospace
                fontSize = 13.px
                lineHeight = 1.4.em
            }

            div {
                ref = gutterRef
                css {
                    padding = Padding(6.px, 8.px)
                    textAlign = TextAlign.right
                    color = Color("rgb(128, 128, 128)")
                    userSelect = None.none
                    backgroundColor = Color("#f0f0f0")
                    minWidth = 2.5.em
                    whiteSpace = WhiteSpace.pre
                    overflow = Overflow.hidden
                }

                +lineNumbers()
            }

            textarea {
                css {
                    flexGrow = number(1.0)
                    border = None.none
                    outline = None.none
                    padding = 6.px
                    fontFamily = FontFamily.monospace
                    fontSize = 13.px
                    lineHeight = 1.4.em
                    resize = Resize.vertical
                    whiteSpace = WhiteSpace.pre
                    tabSize = 2.toString().unsafeCast<TabSize>()
                    minHeight = 400.px
                    backgroundColor = Color.transparent
                }

                value = props.value
                disabled = props.disabled
                spellCheck = false

                onChange = {
                    props.onChange(it.target.value)
                }

                onScroll = {
                    gutterRef.current?.scrollTop = it.currentTarget.scrollTop
                }

                onKeyDown = handler@ { event ->
                    val isSave = (event.ctrlKey || event.metaKey) &&
                            !event.altKey && !event.shiftKey &&
                            (event.key == "s" || event.key == "S")
                    if (!isSave) {
                        return@handler
                    }
                    event.preventDefault()
                    val onSave = props.onSave
                    if (onSave != null && !props.disabled) {
                        onSave()
                    }
                }
            }
        }

        props.error?.let { errorText ->
            div {
                css {
                    marginTop = 6.px
                    padding = Padding(6.px, 8.px)
                    backgroundColor = Color("rgb(255, 235, 235)")
                    border = Border(1.px, LineStyle.solid, Color("rgb(220, 100, 100)"))
                    color = Color("rgb(170, 20, 20)")
                    fontFamily = FontFamily.monospace
                    fontSize = 12.px
                    whiteSpace = WhiteSpace.preWrap
                }

                +errorText
            }
        }
    }


    private fun lineNumbers(): String {
        val count = (props.value.count { it == '\n' } + 1).coerceAtLeast(1)
        return (1..count).joinToString("\n")
    }
}
