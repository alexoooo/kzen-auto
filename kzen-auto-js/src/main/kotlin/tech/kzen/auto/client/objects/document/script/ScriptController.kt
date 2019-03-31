package tech.kzen.auto.client.objects.document.script

import kotlinx.css.Cursor
import kotlinx.css.Position
import kotlinx.css.em
import kotlinx.css.px
import kotlinx.html.title
import react.*
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledH3
import styled.styledSpan
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionState
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation


@Suppress("unused")
class ScriptController:
        RComponent<RProps, ScriptController.State>(),
        ModelManager.Observer,
        ExecutionManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepsAttributePath = AttributePath.parse("steps")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
            var execution: ExecutionModel?,
            var creating: Boolean
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ):
            DocumentController
    {
        override fun type(): DocumentArchetype {
            return type
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(ScriptController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        console.log("^^^^^^ script - componentDidMount")

//        println("ProjectController - Subscribed")
        async {
//            console.log("^^^^^^ script - adding observers")

            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
            ClientContext.insertionManager.subscribe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        console.log("^^^^^^ script - componentWillUnmount")

//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: ScriptController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
//        println("ProjectController - && handled - " +
//                "${autoModel.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            structure = projectStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
//        console.log("^^^^^^ script - handleNavigation", documentPath)

        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
        async {
            ClientContext.insertionManager.create(
                    state.documentPath!!,
                    index)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val structure = state.structure
                ?: return

        val documentPath: DocumentPath? = state.documentPath

        val documentNotation: DocumentNotation? =
                documentPath.let { structure.graphNotation.documents.values[it] }

        if (documentNotation == null) {
            styledH3 {
                css {
                    marginLeft = 1.em
                    paddingTop = 1.em
                }

                if (structure.graphNotation.documents.values.isEmpty()) {
                    +"Please create a file in the sidebar (left)"
                }
                else {
                    +"Please select a file from the sidebar (left)"
                }
            }
        }
        else {
            styledDiv {
                css {
                    marginLeft = 1.em
                }

                steps(structure, documentNotation, documentPath!!)
            }

            runController()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            graphStructure: GraphStructure,
            documentNotation: DocumentNotation,
            documentPath: DocumentPath
    ) {
        val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

        val stepsNotation = graphStructure
                .graphNotation
                .transitiveAttribute(mainObjectLocation, stepsAttributePath)
                as? ListAttributeNotation
                ?: return

        val stepReferences = stepsNotation.values.map { ObjectReference.parse(it.asString()!!) }

        if (stepReferences.isEmpty()) {
            styledH3 {
                css {
                    paddingTop = 1.em
                }

                +"Empty script, please add steps using action bar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySteps(graphStructure, /*documentNotation,*/ documentPath, stepReferences)
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
//            documentNotation: DocumentNotation,
            documentPath: DocumentPath,
            stepReferences: List<ObjectReference>
    ) {
        insertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

            for ((index, stepReference) in stepReferences.withIndex()) {
                val stepLocation = graphStructure.graphNotation.coalesce.locate(stepReference)
                val objectPath = stepLocation.objectPath

                val status: ExecutionState? =
                        state.execution?.frames?.lastOrNull()?.states?.get(objectPath)

                val keyLocation = ObjectLocation(documentPath, objectPath)

                action(keyLocation,
                        graphStructure,
                        status)

                if (index < stepReferences.size - 1) {
                    downArrowWithInsertionPoint(index + 1)
                }
            }
        }

        insertionPoint(stepReferences.size)
    }


    private fun RBuilder.downArrowWithInsertionPoint(index: Int) {
        styledDiv {
            css {
                position = Position.relative
                height = 4.em
                width = 9.em
            }

            styledDiv {
                css {
                    marginTop = 0.5.em

                    position = Position.absolute
                    height = 1.em
                    width = 1.em
                    top = 0.em
                    left = 0.em
                }
                insertionPoint(index)
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = 8.5.em

                    marginTop =  0.5.em
                    marginBottom = 0.5.em
                }

                child(ArrowDownwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert action here"
                }
            }

//            +"Index: $index"

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        if (! state.creating) {
                            opacity = 0
                            cursor = Cursor.default
                        }
                    }

                    onClick = {
                        onCreate(index)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.action(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            executionState: ExecutionState?
    ) {
        // todo:
//        val actionUiWrapper: ActionWrapper =
//                ActionController.Wrapper()

        span {
            key = objectLocation.toReference().asString()

            child(ActionController::class) {
                attrs {
                    this.objectLocation = objectLocation
                    structure = graphStructure
                    state = executionState
                }
            }

//            actionUiWrapper.render(
//                    this,
//
//                    objectLocation,
//                    graphStructure,
//                    executionState)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.runController() {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(RunController::class) {
                attrs {
                    documentPath = state.documentPath
                }
            }
        }
    }


//    private fun RBuilder.reset() {
//        input (type = InputType.button) {
//            attrs {
//                value = "Clear"
//                onClickFunction = { onClear() }
//            }
//        }
//    }
//
//
//    private fun RBuilder.refresh() {
//        input (type = InputType.button) {
//            attrs {
//                value = "Reload"
//                onClickFunction = { onRefresh() }
//            }
//        }
//    }
}