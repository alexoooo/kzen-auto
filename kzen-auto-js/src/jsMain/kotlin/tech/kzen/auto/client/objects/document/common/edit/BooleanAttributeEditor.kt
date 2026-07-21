package tech.kzen.auto.client.objects.document.common.edit

import mui.material.InputLabel
import mui.material.Switch
import mui.material.SwitchColor
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface BooleanAttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var value: Boolean

    var trueLabelOverride: String?
    var falseLabelOverride: String?

    var disabled: Boolean

    var onChange: ((Boolean) -> Unit)?

    var mirroredGraphStore: MirroredGraphStore
}


external interface BooleanAttributeEditorState: State {
    // Non-null once a write failed, turning the label red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
class BooleanAttributeEditor(
    props: BooleanAttributeEditorProps
):
    RPureComponent<BooleanAttributeEditorProps, BooleanAttributeEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    // The toggle always carries its own value, so there is no pending buffer to read: schedule/flush are never
    // called and only the explicit-value commitNow is used.
    private val committer = AttributeCommitter(
        graphStore = { props.mirroredGraphStore },
        objectLocation = { props.objectLocation },
        attributePath = { props.attributePath },
        pendingNotation = { null },
        onCommitted = { props.onChange?.invoke((it as ScalarAttributeNotation).value.toBoolean()) },
        onError = { message -> setState { errorMessage = message } })


    //-----------------------------------------------------------------------------------------------------------------
    private fun submitEditAsync(newValue: Boolean) {
        if (props.value == newValue) {
            return
        }

        async {
            committer.commitNow(ScalarAttributeNotation(newValue.toString()))
        }
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
        InputLabel {
            sx {
                fontSize = 0.8.em
            }

            error = state.errorMessage != null

            +formattedLabel()

            Switch {
                checked = props.value
                disabled = props.disabled
                onChange = { e, _ ->
                    val target = e.currentTarget
                    submitEditAsync(target.checked)
                }
                color = SwitchColor.default

                if (props.value) {
                    sx {
                        this.color = NamedColor.black
                    }
                }
            }
        }
    }
}