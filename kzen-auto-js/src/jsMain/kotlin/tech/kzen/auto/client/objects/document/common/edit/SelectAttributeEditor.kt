package tech.kzen.auto.client.objects.document.common.edit

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SelectAttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var value: String
    var options: Map<String, String>

    var labelOverride: String?

    var disabled: Boolean
    var invalid: Boolean

    var onChange: ((String) -> Unit)?

    var mirroredGraphStore: MirroredGraphStore
}


external interface SelectAttributeEditorState: State {
    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
class SelectAttributeEditor(
    props: SelectAttributeEditorProps
):
    RPureComponent<SelectAttributeEditorProps, SelectAttributeEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    // The selection always carries its own value, so there is no pending buffer to read: schedule/flush are never
    // called and only the explicit-value commitNow is used.
    // NB: `this.props` - see the shadowing note in TextAttributeEditor.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { this.props.attributePath },
        pendingNotation = { null },
        onCommitted = { this.props.onChange?.invoke((it as ScalarAttributeNotation).value) },
        onError = { message -> setState { errorMessage = message } },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun submitEditAsync(newValue: String) {
        if (props.value == newValue) {
            return
        }

        async {
            committer.commitNow(ScalarAttributeNotation(newValue))
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val selectedOption: SelectOption = unsafeJso {
            value = props.value
            label = props.options[props.value] ?: props.value
        }

        val selectOptions = props.options.map {
            val option: SelectOption = unsafeJso {
                value = it.key
                label = it.value
            }
            option
        }.toTypedArray()

        div {
            css {
                width = 16.em
            }

            muiAutocompleteField(
                label = formattedLabel(),
                options = selectOptions,
                selectedOption = selectedOption,
                onSelect = { submitEditAsync(it.value) },
                disabled = props.disabled,
                error = props.invalid || state.errorMessage != null,
                disableClearable = true)
        }
    }
}