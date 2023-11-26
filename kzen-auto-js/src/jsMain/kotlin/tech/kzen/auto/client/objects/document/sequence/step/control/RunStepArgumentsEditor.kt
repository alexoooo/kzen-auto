package tech.kzen.auto.client.objects.document.sequence.step.control

import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface RunStepArgumentsEditorState: State {
    var initialized: Boolean
    var renaming: Boolean

    var values: PersistentMap<String, ObjectLocation>?
    var predecessors: PersistentList<ObjectLocation>?
    var parameterNames: PersistentList<String>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class RunStepArgumentsEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, RunStepArgumentsEditorState>(props),
    LocalGraphStore.Observer,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val stepIdentifier = ObjectName("SequenceStep")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            RunStepArgumentsEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RunStepArgumentsEditorState.init(props: AttributeEditorProps) {
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

        initialized = false
        renaming = false

        values = null
        predecessors = null
        parameterNames = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: RunStepArgumentsEditorState,
        snapshot: Any
    ) {
        if (state.values != prevState.values) {
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

        val attributeMap: Map<String, String> =
            (attributeNotation as? MapAttributeNotation)
            ?.values
            ?.map { it.key.asKey() to (it.value as ScalarAttributeNotation).value }
            ?.toMap()
            ?: mapOf()

        val values = mutableMapOf<String, ObjectLocation>()
        for (e in attributeMap) {
            val reference = ObjectReference.parse(e.value)

            val value: ObjectLocation? =
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)

            if (value != null) {
                values[e.key] = value
            }
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
                    it.objectPath.name == SequenceConventions.stepObjectName
                }
            }

        val predecessors = steps
            .filter { it != props.objectLocation.objectPath }
            .map { host.toObjectLocation(it) }

        setState {
            initialized = true

            this.values = values.toPersistentMap()
            this.predecessors = predecessors.toPersistentList()
            parameterNames = attributeMap.keys.toPersistentList()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        val values = state.values
            ?: return

        when (event) {
            is RenamedObjectRefactorEvent -> {
                val entryList = values.entries.toList()
                val renamedEntry = entryList.find { it.value == event.renamedObject.objectLocation }
                    ?: return

                setState {
                    this.values = values.put(
                        renamedEntry.key, event.renamedObject.newObjectLocation())
                    renaming = true
                }
            }

            else -> {}
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(parameterName: String, value: ObjectLocation) {
//        console.log("onValueChange - $value")
        val values = state.values
            ?: return

        setState {
            this.values = values.put(parameterName, value)
        }
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val values = state.values
            ?: return

        val localReferences: PersistentMap<AttributeSegment, AttributeNotation> =
            values
            .entries
            .map {
                val localReference = it.value.toReference().crop(retainPath = false)
                AttributeSegment.ofKey(it.key) to
                        ScalarAttributeNotation(localReference.asString())
            }
            .toPersistentMap()

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                MapAttributeNotation(localReferences)))
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
        val values = state.values ?: return
        val predecessors = state.predecessors ?: return
        val parameterNames = state.parameterNames ?: return

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


        for (parameterName in parameterNames) {
            div {
                key = parameterName
                renderParameter(parameterName, selectOptions, values)
            }
        }
    }


    private fun ChildrenBuilder.renderParameter(
        parameterName: String,
        selectOptions: Array<ReactSelectOption>,
        values: PersistentMap<String, ObjectLocation>
    ) {
        val selectedValue = values[parameterName]
        val selectedOption = selectOptions.find { it.value == selectedValue?.asString() }

//        val selectId = "material-react-select-id"

        InputLabel {
//            htmlFor = selectId
            css {
                fontSize = 0.8.em
            }

            +parameterName

            ReactSelect::class.react {
//            id = selectId
                value = selectedOption
                options = selectOptions

                onChange = {
                    onValueChange(parameterName, ObjectLocation.parse(it.value))
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


//    private fun formattedLabel(): String {
//        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
//    }
}
