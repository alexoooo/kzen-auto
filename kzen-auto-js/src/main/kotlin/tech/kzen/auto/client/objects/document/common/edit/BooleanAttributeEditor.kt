package tech.kzen.auto.client.objects.document.common.edit

import csstype.NamedColor
import csstype.em
import emotion.react.css
import mui.material.InputLabel
import mui.material.Switch
import mui.material.SwitchColor
import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


//---------------------------------------------------------------------------------------------------------------------
external interface BooleanAttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var value: Boolean

    var trueLabelOverride: String?
    var falseLabelOverride: String?

    var disabled: Boolean

    var onChange: ((Boolean) -> Unit)?
}


//---------------------------------------------------------------------------------------------------------------------
class BooleanAttributeEditor(
    props: BooleanAttributeEditorProps
):
    RPureComponent<BooleanAttributeEditorProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun submitEditAsync(newValue: Boolean) {
        if (props.value == newValue) {
            return
        }

        async {
            submitEdit(newValue)
        }
    }


    private suspend fun submitEdit(newValue: Boolean) {
        val attributeNotation = ScalarAttributeNotation(newValue.toString())

        val command = CommonEditUtils.editCommand(
            props.objectLocation, props.attributePath, attributeNotation)

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

        props.onChange?.invoke(newValue)
    }


    private fun formattedLabel(): String {
        val labelOverride = when (props.value) {
            true -> props.trueLabelOverride
            false -> props.falseLabelOverride
        }

        return CommonEditUtils.formattedLabel(props.attributePath, labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val inputId = "material-react-switch-id"
        InputLabel {
            htmlFor = inputId

            css {
                fontSize = 0.8.em
            }

            +formattedLabel()
        }

        Switch {
            id = inputId
            checked = props.value
            disabled = props.disabled
            onChange = { e, _ ->
                val target = e.target
                submitEditAsync(target.checked)
            }
            color = SwitchColor.default

            if (props.value) {
                css {
//                        this.color = Color("#8CBAE8")
                    this.color = NamedColor.black
                }
            }
//                style = reactStyle {
//                    color = Color("#c4c4c4")
//                }

//                styleOverrides = json(
//                    "track" to reactStyle {
//                        color = Color.red
//                        backgroundColor = Color.green
//                    }
//                )
        }
    }
}