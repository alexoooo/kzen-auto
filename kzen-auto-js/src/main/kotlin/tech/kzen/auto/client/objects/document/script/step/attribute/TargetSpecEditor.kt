package tech.kzen.auto.client.objects.document.script.step.attribute

import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.marginTop
import org.w3c.dom.HTMLInputElement
import react.*
import styled.styledDiv
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.toPersistentMap


@Suppress("unused")
class TargetSpecEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, TargetSpecEditor.State>(props),
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var targetType: TargetType,

            var targetText: String?,
            var targetTextPending: Boolean,

            var targetLocation: ObjectLocation?,
            var targetRenaming: Boolean
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation
    ) :
            AttributeEditorWrapper(objectLocation) {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>): ReactElement {
            return input.child(TargetSpecEditor::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        editAttributeCommandAsync()
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: AttributeEditorProps) {
        val attributeNotation = props
                .graphStructure
                .graphNotation
                .transitiveAttribute(props.objectLocation, props.attributeName)
                as? MapAttributeNotation
                ?: return

        targetType = attributeNotation
                .get(TargetSpecDefiner.typeKey)
                ?.asString()
                ?.let { TargetType.valueOf(it) }
                ?: return

        targetTextPending = false
        targetRenaming = false

        if (targetType == TargetType.Focus) {
            targetText = null
            targetLocation = null
        }
        else {
            val value = attributeNotation
                    .get(TargetSpecDefiner.valueKey)
                    ?.asString()

            if (targetType == TargetType.Text ||
                    targetType == TargetType.Xpath) {
                targetText = value
                targetLocation = null
            }
            else if (value != null) {
                val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)
                val reference = ObjectReference.parse(value)

                targetText = null
                targetLocation = props.graphStructure.graphNotation.coalesce.locate(
                        reference, objectReferenceHost)
            }
            else {
                targetText = null
                targetLocation = null
            }
        }

//        submitDebounce = lodash.debounce({
//            editAttributeCommandAsync()
//        }, 1000)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: AttributeEditorProps,
            prevState: State,
            snapshot: Any
    ) {
        if (state.targetType != prevState.targetType) {
            editAttributeCommandAsync()
        }
        else if (state.targetText != prevState.targetText) {
            submitDebounce.apply()
        }
        else if (state.targetLocation != prevState.targetLocation) {
            if (state.targetRenaming) {
                setState {
                    targetRenaming = false
                }
            }
            else {
                editAttributeCommandAsync()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun flush() {
//        println("ParameterEditor | flush")

        submitDebounce.cancel()
        if (state.targetTextPending) {
            editAttributeCommand()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val attributeMap =
                mutableMapOf<AttributeSegment, ScalarAttributeNotation>()

        attributeMap[TargetSpecDefiner.typeSegment] =
                ScalarAttributeNotation(state.targetType.name)

        if (state.targetText != null) {
            attributeMap[TargetSpecDefiner.valueSegment] =
                    ScalarAttributeNotation(state.targetText!!)
        }
        else if (state.targetLocation != null) {
            val localReference = state.targetLocation!!.toReference()
                    .crop(retainPath = false)

            attributeMap[TargetSpecDefiner.valueSegment] =
                    ScalarAttributeNotation(localReference.asString())
        }

        val attributeNotation = MapAttributeNotation(attributeMap.toPersistentMap())

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                attributeNotation))

        if (state.targetText != null) {
            setState {
                targetTextPending = false
            }
        }
    }


    private fun onTypeChange(newType: TargetType) {
        setState {
            targetType = newType
            targetText = null
            targetLocation = null
        }
    }


    private fun onTextChange(newValue: String) {
        setState {
            targetText = newValue
            targetTextPending = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectLabelId = "material-react-input-label-id"

        styledDiv {
            child(MaterialFormControl::class) {
                child(MaterialInputLabel::class) {
                    attrs {
                        id = selectLabelId
                    }
                    +"Target"
                }

                child(MaterialSelect::class) {
                    attrs {
                        labelId = selectLabelId

                        style = reactStyle {
                            fontSize = 0.8.em
                        }

                        value = state.targetType.name

                        onChange = {
                            val target: dynamic = it.target!!
                            val value = target.value as String
                            onTypeChange(TargetType.valueOf(value))
                        }
                    }

                    for (type in TargetType.values()) {
                        child(MaterialMenuItem::class) {
                            key = type.name

                            attrs {
                                value = type.name
                            }

                            when (type) {
                                TargetType.Focus ->
                                    +"Currently focused"

                                TargetType.Text ->
                                    +"Containing text"

                                TargetType.Xpath ->
                                    +"Matching XPath"

                                TargetType.Visual ->
                                    +"Visual"
                            }
                        }
                    }
                }
            }
        }

        if (state.targetType == TargetType.Text ||
                state.targetType == TargetType.Xpath) {
            child(MaterialTextField::class) {
                attrs {
                    fullWidth = true

                    style = reactStyle {
                        marginTop = 0.5.em
                    }

                    label =
                            if (state.targetType == TargetType.Text) {
                                "Contains"
                            }
                            else {
                                "Matches"
                            }

                    value = state.targetText ?: ""

                    onChange = {
                        val target = it.target as HTMLInputElement
                        onTextChange(target.value)
                    }
                }
            }
        }
        else if (state.targetType == TargetType.Visual) {
            +"Visual: ${state.targetLocation}"
        }
    }
}