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
import tech.kzen.auto.client.objects.document.report.model.ReportState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.service.ClientContext
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
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface SelectLogicEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var options: List<ObjectLocation>?
//    var initialized: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectLogicEditor(
    props: AttributeEditor2Props
):
    RComponent<AttributeEditor2Props, SelectLogicEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditor2Props.() -> Unit) {
            SelectLogicEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectLogicEditorState.init(props: AttributeEditor2Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")

        val graphNotation = ClientContext.clientStateGlobal.current()!!.graphStructure().graphNotation

        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        value =
            if (attributeNotation is ScalarAttributeNotation && attributeNotation.value.isNotEmpty()) {
                val reference = ObjectReference.parse(attributeNotation.value)
                val objectLocation = graphNotation.coalesce.locateOptional(reference, objectReferenceHost)
                objectLocation
            }
            else {
                null
            }

//        value = null
        renaming = false
        options = options(graphNotation)
//        initialized = false
    }


    private fun options(graphNotation: GraphNotation): List<ObjectLocation> {
        val featureMains = mutableListOf<ObjectLocation>()

        for ((path, notation) in graphNotation.documents.values) {
            if (path == props.objectLocation.documentPath) {
                // TODO: avoid suggesting DAG violation?
                continue
            }

            val isLogic =
                SequenceState.isSequence(notation) ||
                ReportState.isReport(notation)

            if (isLogic) {
                featureMains.add(ObjectLocation(
                        path, NotationConventions.mainObjectPath))
            }
        }

        return featureMains
    }

    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditor2Props,
        prevState: SelectLogicEditorState,
        snapshot: Any
    ) {
        if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else {
                editAttributeCommandAsync()
            }
        }
    }


    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                if (event.removedUnderOldName.documentPath == state.value?.documentPath) {
                    val newLocation =
                            state.value!!.copy(documentPath = event.createdWithNewName.destination)

                    setState {
                        value = newLocation
                        renaming = true
                    }
                }
                else {
                    updateOptions()
                }
            }

            else -> {
                updateOptions()
            }
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    private fun updateOptions() {
        val graphNotation = ClientContext.clientStateGlobal.current()!!.graphStructure().graphNotation
        setState {
            options = options(graphNotation)
        }
    }


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

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(value.asString())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

//        +"[Select Script]"
        val options = state.options
            ?: return

        val selectOptions = options
                .map {
                    val option: ReactSelectOption = jso {
                        value = it.asString()
                        label = it.documentPath.name.value
                    }
                    option
                }
                .toTypedArray()

//        +"!! ${selectOptions.map { it.value }}"
//        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"

        val selectId = "material-react-select-id"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
            }

            +formattedLabel()
        }

        ReactSelect::class.react {
            id = selectId
            value = selectOptions.find { it.value == state.value?.asString() }
            this.options = selectOptions

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
