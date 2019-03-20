package tech.kzen.auto.client.objects.script

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
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionState
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.DocumentNotation


class ScriptController(
        props: ScriptController.Props
):
        RComponent<ScriptController.Props, ScriptController.State>(props),
        ModelManager.Observer,
        ExecutionManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(

    ): RProps


    class State(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
            var execution: ExecutionModel?,
            var creating: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
            ClientContext.insertionManager.subscribe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: ScriptController.Props,
            prevState: ScriptController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
//        println("ProjectController - && handled - " +
//                "${autoModel.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            structure = autoModel
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
        if (documentNotation.objects.values.isEmpty()) {
            styledH3 {
                css {
                    paddingTop = 1.em
                }

                +"Empty script, please add steps using action bar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySteps(graphStructure, documentNotation, documentPath)
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
            documentNotation: DocumentNotation,
            documentPath: DocumentPath
    ) {
        insertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

//            val next = state.execution?.next()

            var index = 0
            for (e in documentNotation.objects.values) {
                val status: ExecutionState? =
                        state.execution?.frames?.lastOrNull()?.states?.get(e.key)

                val keyLocation = ObjectLocation(documentPath, e.key)

                action(keyLocation,
                        graphStructure,
                        status/*,
                        next?.bundlePath == bundlePath &&
                                next.objectPath == e.key*/)

                if (index < documentNotation.objects.values.size - 1) {
                    styledDiv {
                        css {
                            position = Position.relative
                            height = 4.em
                            width = 9.em
//                            backgroundColor = Color.blue
//                            backgroundColor = Color.red
//                            zIndex = -1
                        }

                        styledDiv {
                            css {
                                marginTop = 0.5.em

                                position = Position.absolute
                                height = 1.em
                                width = 1.em
                                top = 0.em
                                left = 0.em

//                                zIndex = 1999
//                                float = Float.left
                            }
                            insertionPoint(index + 1)
                        }

                        styledDiv {
                            css {
//                                marginLeft = LinearDimension.auto
//                                marginRight = LinearDimension.auto

                                position = Position.absolute
                                height = 3.em
                                width = 3.em
                                top = 0.em
                                left = 8.5.em

//                            position = Position.absolute
//                            top = 0.px
//                            left = 0.em
//                                display = Display.inline
//                                float = Float.left
//                                marginLeft = 4.75.em
//
//                                width = 3.em
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

                index++
            }
        }

        insertionPoint(documentNotation.objects.values.size)
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert action here"
                }
            }

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