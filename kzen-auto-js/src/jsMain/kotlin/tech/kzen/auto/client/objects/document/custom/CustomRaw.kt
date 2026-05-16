package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.YamlEditor
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import web.cssom.Color
import web.cssom.FontStyle
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomRawProps: Props {
    var editorValue: String
    var modified: Boolean
    var saving: Boolean
    var lastError: String?
    var onEditorChange: (String) -> Unit
    var onSave: () -> Unit
}


external interface CustomRawState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomRaw(
    props: CustomRawProps
):
    RPureComponent<CustomRawProps, CustomRawState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val saving = props.saving
        val modified = props.modified
        val saveDisabled = !modified || saving

        div {
            css {
                marginBottom = 0.5.em
            }

            Button {
                variant = ButtonVariant.contained
                size = Size.small
                disabled = saveDisabled
                onClick = { props.onSave() }
                +(if (saving) "Saving..." else "Save")
            }

            if (modified && !saving) {
                span {
                    css {
                        marginLeft = 1.em
                        fontStyle = FontStyle.italic
                        color = Color("rgb(128, 80, 0)")
                    }
                    +"unsaved changes"
                }
            }
        }

        YamlEditor::class.react {
            value = props.editorValue
            onChange = props.onEditorChange
            onSave = props.onSave
            error = props.lastError
            disabled = props.saving
        }
    }
}
