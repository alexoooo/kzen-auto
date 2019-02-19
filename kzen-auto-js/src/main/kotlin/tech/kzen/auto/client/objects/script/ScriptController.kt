package tech.kzen.auto.client.objects.script

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.h3
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionState
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.CreateBundleCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.BundleNotation


class ScriptController(
        props: ScriptController.Props
):
        RComponent<ScriptController.Props, ScriptController.State>(props),
        ModelManager.Observer,
        ExecutionManager.Observer,
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var bundlePath: BundlePath
    ): RProps


    class State(
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
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionManager.unSubscribe(this)
    }


    override fun componentDidUpdate(
            prevProps: ScriptController.Props,
            prevState: ScriptController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)

        if (state.structure != null &&
                state.structure!!.graphNotation.bundles.values[props.bundlePath] == null &&
                (prevState.structure == null ||
                        prevState.structure!!.graphNotation.bundles.values[props.bundlePath] != null)) {
            async {
                createBundle()
            }
        }
    }


    private suspend fun createBundle() {
        ClientContext.commandBus.apply(CreateBundleCommand(NotationConventions.mainPath))
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
    private fun onCreate(index: Int) {
        async {
            ClientContext.insertionManager.create(
                    NotationConventions.mainPath,
                    index)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val structure = state.structure
                ?: return

        val bundleNotation: BundleNotation? =
                structure.graphNotation.bundles.values[props.bundlePath]

        if (bundleNotation == null) {
            +"Initializing..."
        }
        else {
            styledDiv {
                css {
                    marginLeft = 1.em
                }

                steps(state.structure!!, bundleNotation)
            }

            runController()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            graphStructure: GraphStructure,
            bundleNotation: BundleNotation
    ) {
        if (bundleNotation.objects.values.isEmpty()) {
            h3 {
                +"Empty script, please add steps using action bar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySteps(graphStructure, bundleNotation)
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
            bundleNotation: BundleNotation
    ) {
        insertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

//            val next = state.execution?.next()

            var index = 0
            for (e in bundleNotation.objects.values) {
                val status: ExecutionState? =
                        state.execution?.frames?.lastOrNull()?.states?.get(e.key)

                val keyLocation = ObjectLocation(props.bundlePath, e.key)

                action(keyLocation,
                        graphStructure,
                        status/*,
                        next?.bundlePath == bundlePath &&
                                next.objectPath == e.key*/)

                if (index < bundleNotation.objects.values.size - 1) {
                    styledDiv {
                        css {
                            marginTop =  0.5.em
                            float = Float.left
                        }
                        insertionPoint(index + 1)
                    }

                    styledDiv {
                        css {
                            marginLeft = LinearDimension.auto
                            marginRight = LinearDimension.auto
                            display = Display.block

                            width = 3.em
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

                index++
            }
        }

        insertionPoint(bundleNotation.objects.values.size)
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                title = "Insert action here"
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

            child(RunController::class) {}
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