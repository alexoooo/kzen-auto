package tech.kzen.auto.client.objects.document.common.attribute

import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor2
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames.topLevel


//---------------------------------------------------------------------------------------------------------------------
external interface AutoAttributeEditorProps: AttributeEditor2Props {
    var labelOverride: String?
    var disabled: Boolean
    var onChange: ((AttributeNotation) -> Unit)?
    var invalid: Boolean
}

external interface AutoAttributeEditorState: State {
    var attributeMetadata: AttributeMetadata?
    var attributeNotation: AttributeNotation?
}


//---------------------------------------------------------------------------------------------------------------------
class AutoAttributeEditor(
    props: AutoAttributeEditorProps
):
    RPureComponent<AutoAttributeEditorProps, AutoAttributeEditorState>(props),
//    ExecutionRepository.Observer,
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("AutoAttributeEditor")
    }

    private var attributePathValueEditor: RefObject<AttributePathValueEditor2> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditor2Props.() -> Unit) {
            AutoAttributeEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
//        async {
//            ClientContext.executionRepository.observe(this)
//        }
    }


    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun onClientState(clientState: SessionState) {
        val graphStructure = clientState.graphStructure()

        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: containing step was deleted, but its parent component hasn't re-rendered yet
            return
        }

        val attributeMetadata: AttributeMetadata? = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)

        val attributeNotation: AttributeNotation? = graphStructure
            .graphNotation
            .mergeAttribute(props.objectLocation, props.attributeName)

        setState {
            this.attributeMetadata = attributeMetadata
            this.attributeNotation = attributeNotation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AutoAttributeEditorProps,
        prevState: AutoAttributeEditorState,
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
        val attributeMetadata: AttributeMetadata = state.attributeMetadata
            ?: return

        val attributeNotation: AttributeNotation? = state.attributeNotation

        val type = attributeMetadata.type

        when {
            type == null -> {
                +"'${props.attributeName}' (unknown type)"
            }

            attributeMetadata.definerReference?.name?.value == "Self" -> {
                // NB: don't render
            }

            AttributePathValueEditor2.isValue(type) -> {
                renderValueEditor(type)
            }

            else -> {
                +"'${props.attributeName}' (type not supported)"

                div {
                    +"value: ${attributeNotation?.asString() ?: "<missing>"}"
                    ReactHTML.br {}
                    +"type: ${attributeMetadata.type?.className?.topLevel()}"
                    ReactHTML.br {}
                    +"generics: ${attributeMetadata.type?.generics?.map { it.className.get() }}"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderValueEditor(type: TypeMetadata) {
//        +"[AttributePathValueEditor]"

        AttributePathValueEditor2::class.react {
            labelOverride = formattedLabel()
            disabled = props.disabled
            invalid = props.invalid

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