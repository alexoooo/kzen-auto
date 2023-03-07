package tech.kzen.auto.client.objects.document.common
//
//import react.RBuilder
//import react.RHandler
//import react.RPureComponent
//import react.State
//import tech.kzen.auto.client.api.ReactWrapper
//import tech.kzen.lib.common.model.attribute.AttributePath
//import tech.kzen.lib.common.model.obj.ObjectName
//import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
//import tech.kzen.lib.common.reflect.Reflect
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface AttributeControllerProps: AttributeEditorProps {
//    var attributeEditors: List<AttributeEditorWrapper>
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class AttributeController(
//        props: AttributeControllerProps
//):
//        RPureComponent<AttributeControllerProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val editorAttributePath = AttributePath.parse("editor")
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    @Reflect
//    class Wrapper(
//        private val attributeEditors: List<AttributeEditorWrapper>
//    ):
//        ReactWrapper<AttributeControllerProps>
//    {
//        override fun child(input: RBuilder, handler: RHandler<AttributeControllerProps>) {
//            input.child(AttributeController::class) {
//                attrs {
//                    this.attributeEditors = this@Wrapper.attributeEditors
//                }
//
//                handler()
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        val attributeMetadata: AttributeMetadata = props
//            .clientState
//            .graphStructure()
//            .graphMetadata
//            .get(props.objectLocation)
//            ?.attributes
//            ?.get(props.attributeName)
//            ?: return
//
//        val editorAttributeNotation = attributeMetadata
//                .attributeMetadataNotation
//                .get(editorAttributePath.toNesting())
//
//        val editorWrapperName = editorAttributeNotation
//                ?.asString()
//                ?.let { ObjectName(it) }
//                ?: DefaultAttributeEditor.wrapperName
//
//        val editorWrapper = props.attributeEditors.find { it.name() == editorWrapperName }
//                ?: throw IllegalStateException("Attribute editor not found: $editorWrapperName")
//
//        editorWrapper.child(this) {
//            attrs {
//                clientState = props.clientState
//                objectLocation = props.objectLocation
//                attributeName = props.attributeName
//            }
//        }
//    }
//}