package tech.kzen.auto.client.objects.script

import kotlinx.css.Color
import kotlinx.css.Visibility
import kotlinx.css.em
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionPhase
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class RunController:
        RComponent<RProps, RunController.State>(),
        ModelManager.Observer,
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var structure: GraphStructure?,
            var execution: ExecutionModel?,
            var fabHover: Boolean
    ): RState


    enum class Phase {
        Empty,
        Pending,
        Partial,
        Running,
        Looping,
        Done
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
        setState {
            structure = autoModel
        }
    }


    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: RunController.State,
            snapshot: Any
    ) {
        console.log("ProjectController componentDidUpdate", state, prevState)
        if (state.execution == null) {
            return
        }

        if (state.execution!!.frames.isEmpty()) {
//            console.log("!@#!#!@#!@#!@  starting execution")
            async {
                executionStateToFreshStart()
            }
            return
        }
    }


    //-----------------------------------------------------------------------------------------------------------------

    // TODO: refresh manager?
    private fun onRefresh() {
        ClientContext.executionLoop.pause()

        async {
            ClientContext.modelManager.refresh()
            ClientContext.executionManager.reset()
        }
    }


    private suspend fun executionStateToFreshStart() {
        val expectedDigest = ClientContext.executionManager.start(
                NotationConventions.mainPath, state.structure!!)

        val actualDigest = ClientContext.restClient.startExecution()

//        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())

        if (expectedDigest != actualDigest) {
            // TODO
            console.log("Digest mismatch, refresh required")
            // onRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun phase(): Phase {
        val executionModel = state.execution
                ?: return Phase.Empty

        if (executionModel.frames.isEmpty()) {
            return Phase.Empty
        }

        if (executionModel.containsStatus(ExecutionPhase.Running)) {
            if (ClientContext.executionLoop.running()) {
                return Phase.Looping
            }
            return Phase.Running
        }

        if (executionModel.next() == null) {
            return Phase.Done
        }

        if (executionModel.containsStatus(ExecutionPhase.Success) ||
                executionModel.containsStatus(ExecutionPhase.Error)) {
            return Phase.Partial
        }

        return Phase.Pending
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onPause() {
        ClientContext.executionLoop.pause()
    }


    private fun onClear() {
        onPause()

        async {
            ClientContext.executionManager.reset()
            executionStateToFreshStart()
        }
    }


    private fun onRunAll() {
        async {
//            executionStateToFreshStart()

            ClientContext.executionIntent.clear()
            ClientContext.executionLoop.run()
        }
    }



    private fun onOuterEnter() {
        setState {
            fabHover = true
        }
    }

    private fun onOuterLeave() {
        setState {
            fabHover = false
        }
    }


    private fun onFabEnter() {
        val nextToRun = state.execution?.next()
        if (nextToRun == ClientContext.executionIntent.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.set(nextToRun)
        }
    }


    private fun onFabLeave() {
        val nextToRun = state.execution?.next()
//        println("^$%^$%^% onRunAllLeave - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.clearIf(nextToRun)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val phase = phase()
//        println("#$#%#$%#$% phase - $phase")

        if (phase == Phase.Empty) {
            return
        }

        div {
            attrs {
                onMouseOverFunction = {
                    onOuterEnter()
                }
                onMouseOutFunction = {
                    onOuterLeave()
                }
            }

            if (phase == Phase.Partial) {
                child(MaterialIconButton::class) {
                    attrs {
                        title = "Reset"

                        style = reactStyle {
                            if (! state.fabHover) {
                                visibility = Visibility.hidden
                            }

//                            marginLeft = (-0.5).em
                            marginRight = (-0.5).em
                        }

                        onClick = ::onClear
                    }

                    child(ReplayIcon::class) {
                        attrs {
                            style = reactStyle {
//                                marginTop = 1.em
                                fontSize = 1.5.em
                            }
                        }
                    }
                }
            }

            child(MaterialFab::class) {
                val hasMoreToRun = phase == Phase.Pending || phase == Phase.Partial
                val looping = phase == Phase.Looping

                attrs {
                    title =
                            if (phase == Phase.Done) {
                                "Reset"
                            }
                            else if (looping) {
                                "Pause"
                            }
                            else if (phase == Phase.Pending) {
                                "Run all"
                            }
                            else {
                                "Continue"
                            }

                    onClick = {
                        if (hasMoreToRun) {
                            onRunAll()
                        }
                        else if (phase == Phase.Done) {
                            onClear()
                        }
                        else if (looping) {
                            onPause()
                        }
                    }

                    onMouseOver = ::onFabEnter
                    onMouseOut = ::onFabLeave


                    style = reactStyle {
                        backgroundColor =
                                if (hasMoreToRun || looping) {
                                    Color.gold
                                }
                                else {
//                                Color("#649fff")
                                    Color.white
                                }

                        width = 5.em
                        height = 5.em
                    }
                }

                if (hasMoreToRun) {
                    child(PlayArrowIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }
                else if (looping) {
                    child(PauseIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }
                else {
                    child(ReplayIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }
            }
        }
    }
}