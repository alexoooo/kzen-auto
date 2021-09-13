package tech.kzen.auto.client.objects.document.common


import react.*
import react.dom.br
import react.dom.div
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames.topLevel


class DefaultAttributeEditor(
        props: Props
):
        RPureComponent<DefaultAttributeEditor.Props, DefaultAttributeEditor.State>(props),
        ExecutionRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("DefaultAttributeEditor")
    }


    class Props(
        var labelOverride: String?,
        var disabled: Boolean,
        var onChange: ((AttributeNotation) -> Unit)?,
        var invalid: Boolean,

        clientState: SessionState,
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): AttributeEditorProps(
        clientState, objectLocation, attributeName//, labelOverride, disabled
    )


    class State: react.State


    private var attributePathValueEditor: RefObject<AttributePathValueEditor> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            objectLocation: ObjectLocation
    ):
            AttributeEditorWrapper(objectLocation)
    {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>)/*: ReactElement*/ {
            input.child(DefaultAttributeEditor::class) {
                handler()
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
        prevProps: Props,
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
    override fun RBuilder.render() {
        val attributeMetadata: AttributeMetadata = props
            .clientState
            .graphStructure()
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?: return

        val type = attributeMetadata.type

        when {
            type == null -> {
                +"${props.attributeName} (type missing)"
            }

            attributeMetadata.definerReference?.name?.value == "Self" -> {
                // NB: don't render
            }

            AttributePathValueEditor.isValue(type) -> {
                renderValueEditor(type)
            }

            else -> {
                +"${props.attributeName} (type not supported)"

                div {
                    +"type: ${attributeMetadata.type?.className?.topLevel()}"
                    br {}
                    +"generics: ${attributeMetadata.type?.generics?.map { it.className.get() }}"
                }
            }
        }
    }


    private fun RBuilder.renderValueEditor(type: TypeMetadata) {
        child(AttributePathValueEditor::class) {
            attrs {
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
            }

            ref = attributePathValueEditor
        }
    }
}
