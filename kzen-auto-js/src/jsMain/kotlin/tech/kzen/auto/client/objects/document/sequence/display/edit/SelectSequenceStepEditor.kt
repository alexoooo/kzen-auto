package tech.kzen.auto.client.objects.document.sequence.display.edit

import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor2Props
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface SelectSequenceStepEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var initialized: Boolean
    var predecessors: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectSequenceStepEditor(
    props: AttributeEditor2Props
):
    RComponent<AttributeEditor2Props, SelectSequenceStepEditorState>(props),
    LocalGraphStore.Observer,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepIdentifier = ObjectName("SequenceStep")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditor2Props.() -> Unit) {
            SelectSequenceStepEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectSequenceStepEditorState.init(props: AttributeEditor2Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")

//        val attributeNotation = props.clientState.graphStructure().graphNotation
//            .firstAttribute(props.objectLocation, props.attributeName)
////        console.log("SelectSequenceStepEditorState.init | attributeNotation - $attributeNotation")
//
//        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)
//
//        if (attributeNotation is ScalarAttributeNotation) {
//            val reference = ObjectReference.parse(attributeNotation.value)
//            val objectLocation = props.clientState.graphStructure().graphNotation.coalesce
//                .locateOptional(reference, objectReferenceHost)
//
//            if (objectLocation != null) {
//                // NB: might be absent if e.g. it was deleted
//                value = objectLocation
//            }
//        }

        value = null
        renaming = false
        initialized = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditor2Props,
        prevState: SelectSequenceStepEditorState,
        snapshot: Any
    ) {
        if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else if (prevState.initialized) {
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
    }



//    override fun onSequenceState(sequenceState: SequenceState) {
//        sequenceState.progress
//    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing step deleted or renamed and this objectLocation is stale
            return
        }

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val value =
            (attributeNotation as? ScalarAttributeNotation)?.let {
                val reference = ObjectReference.parse(it.value)
                graphNotation.coalesce
                    .locateOptional(reference, objectReferenceHost)
            }

        val host = props.objectLocation.documentPath
        val documentNotation = graphNotation.documents[host]!!

        val documentObjectNotations = documentNotation.objects.notations.values

        val steps = documentObjectNotations
            .keys
            .filter { objectPath ->
                graphNotation.inheritanceChain(
                    host.toObjectLocation(objectPath)
                ).any {
                    it.objectPath.name == stepIdentifier
                }
            }

        val predecessors = steps
            .filter { it != props.objectLocation.objectPath }
            .map { host.toObjectLocation(it) }

        setState {
            this.value = value
            this.predecessors = predecessors
            initialized = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            is RenamedObjectRefactorEvent -> {
                if (event.renamedObject.objectLocation == state.value) {
                    setState {
                        value = event.renamedObject.newObjectLocation()
                        renaming = true
                    }
                }
            }

            else -> {}
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
//        console.log("onValueChange - $value")

        setState {
            this.value = value
        }
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val value = state.value
                ?: return

        val localReference = value.toReference()
                .crop(retainPath = false)

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(localReference.asString())))
    }


//    private fun predecessors(): List<ObjectLocation> {
//        val host = props.objectLocation.documentPath
//        val steps = ControlTree.readSteps(
//                props.clientState.graphStructure(), host)
//
////        console.log("^^^^ steps", steps.toString())
//
//        val objectPaths = steps.predecessors(props.objectLocation.objectPath)
//        return objectPaths.map { ObjectLocation(host, it) }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

        val predecessors = state.predecessors
            ?: return

        val selectOptions: Array<ReactSelectOption> = predecessors
            .map { location ->
                val option: ReactSelectOption = jso {
                    this.value = location.asString()
                    this.label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray()

//        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"

        val selectId = "material-react-select-id"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
            }

            +formattedLabel()
        }

        val selectedValue = selectOptions.find { it.value == state.value?.asString() }
//        console.log("### ReactSelect !!!", state.value, selectedValue, selectOptions)

        ReactSelect::class.react {
            id = selectId
            value = selectedValue
            options = selectOptions

            onChange = {
                onValueChange(ObjectLocation.parse(it.value))
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


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
