package tech.kzen.auto.client.objects.document.custom.raw

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
    var rawStore: CustomRawStore
    var rawState: CustomRawState
    var editorModified: Boolean
}


external interface CustomRawComponentState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomRaw(
    props: CustomRawProps
):
    RPureComponent<CustomRawProps, CustomRawComponentState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onSave() {
        props.rawStore.onSave()
    }


    private fun onEditorChange(newValue: String) {
        props.rawStore.onEditorChange(newValue)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val saving = props.rawState.saving
        val modified = props.editorModified
        val saveDisabled = !modified || saving

        div {
            css {
                marginBottom = 0.5.em
            }

            Button {
                variant = ButtonVariant.contained
                size = Size.small
                disabled = saveDisabled
                onClick = { onSave() }
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
            value = props.rawState.editorValue
            onChange = ::onEditorChange
            onSave = ::onSave
            error = props.rawState.lastError
            disabled = props.rawState.saving
        }
    }
}
