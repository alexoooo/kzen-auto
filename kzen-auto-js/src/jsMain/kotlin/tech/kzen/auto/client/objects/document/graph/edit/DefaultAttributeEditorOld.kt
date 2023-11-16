package tech.kzen.auto.client.objects.document.graph.edit


import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames.topLevel


//---------------------------------------------------------------------------------------------------------------------
external interface DefaultAttributeEditorOldProps: AttributeEditorPropsOld {
    var labelOverride: String?
    var disabled: Boolean
    var onChange: ((AttributeNotation) -> Unit)?
    var invalid: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class DefaultAttributeEditorOld(
    props: DefaultAttributeEditorOldProps
):
    RPureComponent<DefaultAttributeEditorOldProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("DefaultAttributeEditorOld")
    }


    private var attributePathValueEditor: RefObject<AttributePathValueEditorOld> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditorOld(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorPropsOld.() -> Unit) {
            DefaultAttributeEditorOld::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun componentDidMount() {
//        async {
//            ClientContext.executionRepository.observe(this)
//        }
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: DefaultAttributeEditorOldProps,
        prevState: State,
        snapshot: Any
    ) {
        if (props.objectLocation != prevProps.objectLocation) {
            state.init(props)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
//        attributePathValueEditor.current?.flush()
//    }
//
//
//    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel?) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(
            AttributePath.ofName(props.attributeName),
            props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphStructure = props
            .clientState
            .graphStructure()

        val attributeMetadata: AttributeMetadata = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?: return

        val attributeNotation: AttributeNotation? = graphStructure
            .graphNotation
            .mergeAttribute(props.objectLocation, props.attributeName)

        val type = attributeMetadata.type

        when {
            type == null -> {
                +"'${props.attributeName}' (unknown type)"
            }

            attributeMetadata.definerReference?.name?.objectName?.value == "Self" -> {
                // NB: don't render
            }

            AttributePathValueEditorOld.isValue(type) -> {
                renderValueEditor(type)
            }

            else -> {
                +"'${props.attributeName}' (type not supported)"

                div {
                    +"value: ${attributeNotation?.asString() ?: "<missing>"}"
                    br {}
                    +"type: ${attributeMetadata.type?.className?.topLevel()}"
                    br {}
                    +"generics: ${attributeMetadata.type?.generics?.map { it.className.get() }}"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderValueEditor(type: TypeMetadata) {
        AttributePathValueEditorOld::class.react {
            labelOverride = formattedLabel()
            disabled = props.disabled
            invalid = props.invalid

            clientState = props.clientState
            objectLocation = props.objectLocation
            attributePath = AttributePath.ofName(props.attributeName)

            valueType = type

            onChange = {
                props.onChange?.invoke(it)
            }

            ref = attributePathValueEditor
        }
    }
}
