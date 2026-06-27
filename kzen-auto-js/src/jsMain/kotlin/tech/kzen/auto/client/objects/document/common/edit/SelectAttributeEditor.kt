package tech.kzen.auto.client.objects.document.common.edit

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.SelectOption
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


//---------------------------------------------------------------------------------------------------------------------
class SelectAttributeEditor(
    props: SelectAttributeEditorProps
):
    RPureComponent<SelectAttributeEditorProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun submitEditAsync(newValue: String) {
        if (props.value == newValue) {
            return
        }

        async {
            submitEdit(newValue)
        }
    }


    private suspend fun submitEdit(newValue: String) {
        val attributeNotation = ScalarAttributeNotation(newValue)

        val command = CommonEditUtils.editCommand(
            props.objectLocation, props.attributePath, attributeNotation)

        // TODO: handle error
        props.mirroredGraphStore.apply(command)

        props.onChange?.invoke(attributeNotation.value)
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
                disableClearable = true)
        }
    }
}