package tech.kzen.auto.client.objects.document.common

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.reflect.Reflect


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

        clientState: SessionState,
        objectLocation: ObjectLocation,
        attributeName: AttributeName//,
//        labelOverride: String?,
//        disabled: Boolean
    ): AttributeEditorProps(
        clientState, objectLocation, attributeName//, labelOverride, disabled
    )


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
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
        val attributeMetadata: AttributeMetadata = props
            .clientState
            .graphStructure()
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?: return

        val editorAttributeNotation = attributeMetadata
                .attributeMetadataNotation
                .get(editorAttributePath)

        val editorWrapperName = editorAttributeNotation
                ?.asString()
                ?.let { ObjectName(it) }
                ?: DefaultAttributeEditor.wrapperName

        val editorWrapper = props.attributeEditors.find { it.name() == editorWrapperName }
                ?: throw IllegalStateException("Attribute editor not found: $editorWrapperName")

        editorWrapper.child(this) {
            attrs {
                clientState = props.clientState
                objectLocation = props.objectLocation
                attributeName = props.attributeName
            }
        }
    }
}