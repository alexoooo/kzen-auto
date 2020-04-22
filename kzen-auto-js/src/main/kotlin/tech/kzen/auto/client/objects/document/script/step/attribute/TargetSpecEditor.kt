package tech.kzen.auto.client.objects.document.script.step.attribute

import kotlinx.css.em
import kotlinx.css.fontSize
import org.w3c.dom.HTMLInputElement
import react.*
import styled.styledDiv
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.browser.document
import kotlin.js.Json
import kotlin.js.json


@Suppress("unused")
class TargetSpecEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, TargetSpecEditor.State>(props),
        LocalGraphStore.Observer,
        ExecutionRepository.Observer
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
    @Reflect
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
                targetLocation = props.graphStructure.graphNotation.coalesce.locateOptional(
                        reference, objectReferenceHost)
            }
            else {
                targetText = null
                targetLocation = null
            }
        }
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


    override fun componentDidMount() {
        async {
            ClientContext.executionRepository.observe(this)
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionRepository.unobserve(this)
        ClientContext.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                if (event.removedUnderOldName.documentPath == state.targetLocation?.documentPath) {
                    val newLocation =
                            state.targetLocation!!.copy(documentPath = event.createdWithNewName.destination)

                    setState {
                        targetLocation = newLocation
                        targetRenaming = true
                    }
                }
            }
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
        flush()
    }

    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {

    }


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
            val globalReference = state.targetLocation!!.toReference()

            attributeMap[TargetSpecDefiner.valueSegment] =
                    ScalarAttributeNotation(globalReference.asString())
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


    private fun onVisualFeatureChange(value: ObjectLocation?) {
        setState {
            targetLocation = value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun visualTargets(): List<ObjectLocation> {
        val featureMains = mutableListOf<ObjectLocation>()

        for ((path, notation) in
                props.graphStructure.graphNotation.documents.values) {

            if (FeatureDocument.isFeature(notation)) {
                featureMains.add(ObjectLocation(
                        path, NotationConventions.mainObjectPath))
            }
        }

        return featureMains
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        renderSelectType()

        if (state.targetType == TargetType.Text ||
                state.targetType == TargetType.Xpath) {
            renderTextual()
        }
        else if (state.targetType == TargetType.Visual) {
            renderVisualSelect()
        }
    }


    private fun RBuilder.renderSelectType() {
//        val selectLabelId = "material-react-input-label-id"

        styledDiv {
//            child(MaterialFormControl::class) {
//                child(MaterialInputLabel::class) {
//                    attrs {
//                        id = selectLabelId
//                    }
//                    +"Target"
//                }

                child(MaterialSelect::class) {
                    attrs {
//                        labelId = selectLabelId

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
//            }
        }
    }


    private fun RBuilder.renderTextual() {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true

//                style = reactStyle {
//                    marginTop = 0.5.em
//                }

//                label =
//                        if (state.targetType == TargetType.Text) {
//                            "Contains"
//                        }
//                        else {
//                            "Matches"
//                        }

                value = state.targetText ?: ""

                onChange = {
                    val target = it.target as HTMLInputElement
                    onTextChange(target.value)
                }
            }
        }
    }


    private fun RBuilder.renderVisualSelect() {
        val selectOptions = visualTargets()
                .map { ReactSelectOption(it.asString(), it.documentPath.name.value) }
                .toTypedArray()

//        +"!! ${selectOptions.map { it.value }}"
//        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"

//        val selectId = "material-react-select-id"

//        child(MaterialInputLabel::class) {
//            attrs {
//                htmlFor = selectId
//
//                style = reactStyle {
//                    fontSize = 0.8.em
//                }
//            }
//
//            +"Select"
//        }

        child(ReactSelect::class) {
            attrs {
//                id = selectId

                value = selectOptions.find { it.value == state.targetLocation?.asString() }

                options = selectOptions

                onChange = {
                    onVisualFeatureChange(ObjectLocation.parse(it.value))
                }

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, _ ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles

                // NB: this was causing clipping when used in ConditionalStepDisplay table,
                //   see: https://react-select.com/advanced#portaling
                menuPortalTarget = document.body!!
            }
        }
    }
}