package tech.kzen.auto.client.objects.document.common


import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames.topLevel


//---------------------------------------------------------------------------------------------------------------------
external interface DefaultAttributeEditorProps: AttributeEditorProps {
    var labelOverride: String?
    var disabled: Boolean
    var onChange: ((AttributeNotation) -> Unit)?
    var invalid: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class DefaultAttributeEditor(
        props: DefaultAttributeEditorProps
):
        RPureComponent<DefaultAttributeEditorProps, State>(props),
        ExecutionRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("DefaultAttributeEditor")
    }


    private var attributePathValueEditor: RefObject<AttributePathValueEditor> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditorWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DefaultAttributeEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.executionRepository.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionRepository.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: DefaultAttributeEditorProps,
        prevState: State,
        snapshot: Any
    ) {
        if (props.objectLocation != prevProps.objectLocation) {
            state.init(props)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
        attributePathValueEditor.current?.flush()
    }


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel?) {}


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

            attributeMetadata.definerReference?.name?.value == "Self" -> {
                // NB: don't render
            }

            AttributePathValueEditor.isValue(type) -> {
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
        AttributePathValueEditor::class.react {
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
