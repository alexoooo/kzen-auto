package tech.kzen.auto.client.objects.document.common

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.notation.model.AttributeNotation


class AttributeController(
        props: Props
):
        RPureComponent<AttributeController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val editorAttributePath = AttributePath.parse("editor")
    }


    class Props(
            var attributeEditors: List<AttributeEditorWrapper>,

            var objectLocation: ObjectLocation,
            var attributeName: AttributeName,
            var attributeMetadata: AttributeMetadata,
            var attributeNotation: AttributeNotation
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            private val attributeEditors: List<AttributeEditorWrapper>
    ):
            ReactWrapper<Props>
    {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(AttributeController::class) {
                attrs {
                    this.attributeEditors = this@Wrapper.attributeEditors
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val editorWrapperName = props
                .attributeMetadata
                .attributeMetadataNotation
                .get(editorAttributePath)
                ?.asString()
                ?.let { ObjectName(it) }
                ?: DefaultAttributeEditor.wrapperName

        val editorWrapper = props.attributeEditors.find { it.name() == editorWrapperName }
                ?: throw IllegalStateException("Attribute editor not found: $editorWrapperName")

        editorWrapper.child(this) {
            attrs {
                objectLocation = props.objectLocation
                attributeName = props.attributeName
                attributeMetadata = props.attributeMetadata
                attributeNotation = props.attributeNotation
            }
        }
    }
}