package tech.kzen.auto.client.objects.document.script.display.edit


import emotion.react.css
import js.objects.unsafeJso
import mui.material.MenuItem
import mui.material.Select
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.select.reactSelectField
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
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
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface TargetSpecEditorState: State {
    var targetType: TargetType?

    var targetText: String?
    var targetTextPending: Boolean

    var targetLocation: ObjectLocation?
    var targetRenaming: Boolean

    var visualTargets: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class TargetSpecEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, TargetSpecEditorState>(props),
    LocalGraphStore.Observer,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            TargetSpecEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val submitDebounceMillis = 1000
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        editAttributeCommandAsync()
    }, submitDebounceMillis)


    //-----------------------------------------------------------------------------------------------------------------
    override fun TargetSpecEditorState.init(props: AttributeEditorProps) {

    }


    override fun onClientState(clientState: ClientState) {
        val attributeNotation = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? MapAttributeNotation
            ?: return

        val targetType = attributeNotation
            .get(TargetSpecDefiner.typeKey)
            ?.asString()
            ?.let { TargetType.valueOf(it) }
            ?: return

        setState {
            this.targetType = targetType

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
                    targetLocation = clientState.graphStructure().graphNotation.coalesce
                        .locateOptional(reference, objectReferenceHost)
                }
                else {
                    targetText = null
                    targetLocation = null
                }
            }

            visualTargets = visualTargets(clientState)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun visualTargets(clientState: ClientState): List<ObjectLocation> {
        val featureMains = mutableListOf<ObjectLocation>()

        for ((path, notation) in
                clientState.graphStructure().graphNotation.documents.map
        ) {
            if (FeatureDocument.isFeature(notation)) {
                featureMains.add(ObjectLocation(
                    path, NotationConventions.mainObjectPath))
            }
        }

        return featureMains
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: TargetSpecEditorState,
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
        ClientContext.clientStateGlobal.observe(this)
        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
        submitDebounce.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
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

            else -> {}
        }
    }


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


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
                ScalarAttributeNotation(state.targetType!!.name)

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
    override fun ChildrenBuilder.render() {
        renderSelectType()

        if (state.targetType == TargetType.Text ||
                state.targetType == TargetType.Xpath) {
            renderTextual()
        }
        else if (state.targetType == TargetType.Visual) {
            renderVisualSelect()
        }
    }


    private fun ChildrenBuilder.renderSelectType() {
        val targetType = state.targetType
            ?: return

        div {
            Select {
                css {
                    fontSize = 0.8.em
                }

                value = targetType.name

                onChange = { event, _ ->
                    val target: dynamic = event.target
                    val value = target.value as String
                    onTypeChange(TargetType.valueOf(value))
                }

                for (type in TargetType.entries) {
                    MenuItem {
                        key = Key(type.name)
                        value = type.name

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


    private fun ChildrenBuilder.renderTextual() {
        TextField {
            fullWidth = true
            size = Size.small
            value = state.targetText ?: ""

            onChange = {
                val target = it.target as HTMLInputElement
                onTextChange(target.value)
            }
        }
    }


    private fun ChildrenBuilder.renderVisualSelect() {
        val visualTargets = state.visualTargets
            ?: return

        val selectOptions = visualTargets
                .map {
                    val option: ReactSelectOption = unsafeJso {
                        value = it.asString()
                        label = it.documentPath.name.value
                    }
                    option
                }
                .toTypedArray()

        reactSelectField(
            selectedOption = selectOptions.find { it.value == state.targetLocation?.asString() },
            options = selectOptions,
            onSelect = { onVisualFeatureChange(ObjectLocation.parse(it.value)) })
    }
}