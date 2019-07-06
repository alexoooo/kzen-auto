package tech.kzen.auto.client.objects.document.common

import react.RBuilder
import react.RHandler
import react.RState
import react.ReactElement
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata


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

            graphStructure: GraphStructure,
            objectLocation: ObjectLocation,
            attributeName: AttributeName/*,
            attributeMetadata: AttributeMetadata,
            attributeNotation: AttributeNotation?*/
    ): AttributeEditorProps(
            graphStructure,
            objectLocation, attributeName//, attributeMetadata, attributeNotation
    )


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
        val attributeMetadata: AttributeMetadata = props
                .graphStructure
                .graphMetadata
                .get(props.objectLocation)
                ?.attributes
                ?.get(props.attributeName)
                ?: return

        val editorAttributeNotation = attributeMetadata
                .attributeMetadataNotation
                .get(editorAttributePath)

//        +"%% editorAttributeNotation: $editorAttributeNotation"
//        br {}
//        +"%% props.attributeEditors: ${props.attributeEditors.map { it.name().value }}"


        val editorWrapperName = editorAttributeNotation
                ?.asString()
                ?.let { ObjectName(it) }
                ?: DefaultAttributeEditor.wrapperName

        val editorWrapper = props.attributeEditors.find { it.name() == editorWrapperName }
                ?: throw IllegalStateException("Attribute editor not found: $editorWrapperName")

        editorWrapper.child(this) {
            attrs {
                graphStructure = props.graphStructure
                objectLocation = props.objectLocation
                attributeName = props.attributeName
//                attributeMetadata = props.attributeMetadata
//                attributeNotation = props.attributeNotation
            }
        }
    }
}