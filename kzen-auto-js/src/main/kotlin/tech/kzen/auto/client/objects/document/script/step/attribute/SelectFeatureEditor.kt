package tech.kzen.auto.client.objects.document.script.step.attribute

import csstype.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.*
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface SelectFeatureEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectFeatureEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, SelectFeatureEditorState>(props),
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation
    ):
        AttributeEditorWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectFeatureEditor::class.react {
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectFeatureEditorState.init(props: AttributeEditorProps) {
//        console.log("ParameterEditor | State.init - ${props.name}")

        val attributeNotation = props.clientState.graphStructure().graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        if (attributeNotation is ScalarAttributeNotation) {
            val reference = ObjectReference.parse(attributeNotation.value)
            val objectLocation = props.clientState.graphStructure().graphNotation.coalesce
                .locate(reference, objectReferenceHost)

            value = objectLocation
        }

        renaming = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectFeatureEditorState,
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

        val globalReference = value.toReference()

        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(globalReference.asString())))
    }


    private fun options(): List<ObjectLocation> {
        val featureMains = mutableListOf<ObjectLocation>()

        for ((path, notation) in
                props.clientState.graphStructure().graphNotation.documents.values)
        {
            if (FeatureDocument.isFeature(notation)) {
                featureMains.add(ObjectLocation(
                        path, NotationConventions.mainObjectPath))
            }
        }

        return featureMains
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

        val selectOptions = options()
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
