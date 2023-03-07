package tech.kzen.auto.client.objects.document.script.step.attribute
//
//import kotlinx.browser.document
//import kotlinx.css.em
//import kotlinx.css.fontSize
//import react.*
//import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
//import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
//import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.material.MaterialInputLabel
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.client.wrap.select.ReactSelect
//import tech.kzen.auto.client.wrap.select.ReactSelectOption
//import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTree
//import tech.kzen.lib.common.model.attribute.AttributePath
//import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.locate.ObjectReference
//import tech.kzen.lib.common.model.locate.ObjectReferenceHost
//import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
//import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
//import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
//import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
//import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
//import tech.kzen.lib.common.reflect.Reflect
//import tech.kzen.lib.common.service.store.LocalGraphStore
//import kotlin.js.Json
//import kotlin.js.json
//
//
//@Suppress("unused")
//class SelectStepEditor(
//        props: AttributeEditorProps
//):
//        RPureComponent<AttributeEditorProps, SelectStepEditor.State>(props),
//        LocalGraphStore.Observer
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    class State(
//            var value: ObjectLocation?,
//            var renaming: Boolean
//    ): react.State
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    @Reflect
//    class Wrapper(
//            objectLocation: ObjectLocation
//    ):
//            AttributeEditorWrapper(objectLocation)
//    {
//        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>)/*: ReactElement*/ {
//            input.child(SelectStepEditor::class) {
//                handler()
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun State.init(props: AttributeEditorProps) {
////        console.log("ParameterEditor | State.init - ${props.name}")
//
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val attributeNotation = props.clientState.graphStructure().graphNotation
//            .firstAttribute(props.objectLocation, props.attributeName)
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
//
//        renaming = false
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun componentDidUpdate(
//            prevProps: AttributeEditorProps,
//            prevState: State,
//            snapshot: Any
//    ) {
//        if (state.value != prevState.value) {
//            if (state.renaming) {
//                setState {
//                    renaming = false
//                }
//            }
//            else {
//                editAttributeCommandAsync()
//            }
//        }
//    }
//
//    override fun componentDidMount() {
//        async {
//            ClientContext.mirroredGraphStore.observe(this)
//        }
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.mirroredGraphStore.unobserve(this)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
//        when (event) {
//            is RenamedObjectRefactorEvent -> {
//                if (event.renamedObject.objectLocation == state.value) {
//                    setState {
//                        value = event.renamedObject.newObjectLocation()
//                        renaming = true
//                    }
//                }
//            }
//
//            else -> {}
//        }
//    }
//
//
//    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}
//
//
//    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onValueChange(value: ObjectLocation?) {
////        console.log("onValueChange - $value")
//
//        setState {
//            this.value = value
//        }
//    }
//
//
//    private fun editAttributeCommandAsync() {
//        async {
//            editAttributeCommand()
//        }
//    }
//
//
//    private suspend fun editAttributeCommand() {
//        val value = state.value
//                ?: return
//
//        val localReference = value.toReference()
//                .crop(retainPath = false)
//
//        ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
//                props.objectLocation,
//                props.attributeName,
//                ScalarAttributeNotation(localReference.asString())))
//    }
//
//
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
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
////        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
////                props.objectLocation, props.attributeName)
//
//        val selectOptions = predecessors()
//                .map { ReactSelectOption(it.asString(), it.objectPath.name.value) }
//                .toTypedArray()
//
////        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"
//
//        val selectId = "material-react-select-id"
//
//        child(MaterialInputLabel::class) {
//            attrs {
//                htmlFor = selectId
//
//                style = reactStyle {
//                    fontSize = 0.8.em
//                }
//            }
//
//            +formattedLabel()
//        }
//
//        child(ReactSelect::class) {
//            attrs {
//                id = selectId
//
//                value = selectOptions.find { it.value == state.value?.asString() }
////                value = firstOption
//
//                options = selectOptions
////                options = optionsArray
//
//                onChange = {
////                    console.log("^^^^^ selected: $it")
//
//                    onValueChange(ObjectLocation.parse(it.value))
////                    onTypeChange(it.value)
//                }
//
//                // https://stackoverflow.com/a/51844542/1941359
//                val styleTransformer: (Json, Json) -> Json = { base, _ ->
//                    val transformed = json()
//                    transformed.add(base)
//                    transformed["background"] = "transparent"
//                    transformed
//                }
//
//                val reactStyles = json()
//                reactStyles["control"] = styleTransformer
//                styles = reactStyles
//
//                // NB: this was causing clipping when used in ConditionalStepDisplay table,
//                //   see: https://react-select.com/advanced#portaling
//                menuPortalTarget = document.body!!
//            }
//        }
//    }
//
//
//    private fun formattedLabel(): String {
//        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
//    }
//}
