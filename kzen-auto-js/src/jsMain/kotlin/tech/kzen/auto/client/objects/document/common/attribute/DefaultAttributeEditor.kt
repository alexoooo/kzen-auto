package tech.kzen.auto.client.objects.document.common.attribute

import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.edit.BooleanAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.topLevel


//---------------------------------------------------------------------------------------------------------------------
external interface DefaultAttributeEditorState: State {
    var attributeMetadata: AttributeMetadata?
    var attributeNotation: AttributeNotation?
}


//---------------------------------------------------------------------------------------------------------------------
class DefaultAttributeEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, DefaultAttributeEditorState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("DefaultAttributeEditor")

        private const val multilineKey = "multiline"
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DefaultAttributeEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
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

        if (state.attributeMetadata == attributeMetadata && state.attributeNotation == attributeNotation) {
            return
        }

        setState {
            this.attributeMetadata = attributeMetadata
            this.attributeNotation = attributeNotation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun extractValues(attributeNotation: AttributeNotation): Pair<String?, List<String>?> {
        return when (attributeNotation) {
            is ScalarAttributeNotation -> {
                val scalarValue = attributeNotation.value
                scalarValue to null
            }

            is ListAttributeNotation -> {
                if (attributeNotation.values.all { it.asString() != null }) {
                    val stringValues = attributeNotation.values.map { it.asString()!! }

                    null to stringValues
                }
                else {
                    null to null
                }
            }

            is MapAttributeNotation -> TODO()
        }
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

            attributeMetadata.definerReference?.name?.objectName?.value == "Self" -> {
                // don't render
            }

            CommonEditUtils.isValueType(type) -> {
                renderValueEditor(type, attributeNotation, attributeMetadata.attributeMetadataNotation)
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


    private fun ChildrenBuilder.renderValueEditor(
        valueType: TypeMetadata,
        attributeNotation: AttributeNotation?,
        attributeMetadataNotation: MapAttributeNotation
    ) {
        val (scalarValue, listValues) =
            attributeNotation?.let { extractValues(it) }
                ?: (null to null)

        val className = valueType.className
        val path = AttributePath.ofName(props.attributeName)

        when {
            className == ClassNames.kotlinString ||
            className == ClassNames.kotlinInt ||
            className == ClassNames.kotlinLong ||
            className == ClassNames.kotlinDouble -> {
                val multiline = attributeMetadataNotation.get(multilineKey)?.asBoolean() ?: false

                TextAttributeEditor::class.react {
                    objectLocation = props.objectLocation
                    attributePath = path

                    value = scalarValue ?: ""

                    // NB: deliberately not Type.Number for Int/Long/Double - that formats with thousands
                    //  separators, which this editor never did
                    type =
                        if (multiline) {
                            TextAttributeEditor.Type.MultilineText
                        }
                        else {
                            TextAttributeEditor.Type.PlainText
                        }

                    mirroredGraphStore = props.mirroredGraphStore
                }
            }

            className == ClassNames.kotlinBoolean -> {
                BooleanAttributeEditor::class.react {
                    objectLocation = props.objectLocation
                    attributePath = path

                    value = scalarValue == "true"

                    mirroredGraphStore = props.mirroredGraphStore
                }
            }

            else -> {
                check(className == ClassNames.kotlinList || className == ClassNames.kotlinSet)

                MultiTextAttributeEditor::class.react {
                    objectLocation = props.objectLocation
                    attributePath = path

                    value = listValues ?: listOf()
                    unique = className == ClassNames.kotlinSet

                    mirroredGraphStore = props.mirroredGraphStore
                }
            }
        }
    }
}
