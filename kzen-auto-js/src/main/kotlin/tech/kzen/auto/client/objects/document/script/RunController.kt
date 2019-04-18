package tech.kzen.auto.client.objects.document.script

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
import tech.kzen.auto.common.paradigm.imperative.ImerativeControlFlow
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionPhase
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class RunController(
        props: Props
):
        RComponent<RunController.Props, RunController.State>(props),
//        ModelManager.Observer,
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
            var execution: ExecutionModel?
    ): RProps


    class State(
//            var structure: GraphStructure?,
//            var execution: ExecutionModel?,
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
//    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
//        setState {
//            structure = projectStructure
//        }
//    }


    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(
            host: DocumentPath,
            executionModel: ExecutionModel
    ) {
        if (host != props.documentPath) {
            return
        }

        if (! executionModel.containsStatus(ExecutionPhase.Running)) {
            val next = ImerativeControlFlow.next(props.structure!!.graphNotation, executionModel)
            if (next == null &&
                    ClientContext.executionLoop.running(host)) {
//                console.log("!@#!#!@#!@#!@ onExecutionModel - pause at end")
                ClientContext.executionLoop.pause(host)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
//            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
//        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
    }


    override fun componentDidUpdate(
            prevProps: RunController.Props,
            prevState: RunController.State,
            snapshot: Any
    ) {
//        if (props.documentPath != null && props.documentPath != prevState.documentPath) {
//            async {
//                val executionModel = ClientContext.executionManager.executionModel(state.documentPath!!)
//                setState {
//                    execution = executionModel
//                }
//            }
//        }
//        console.log("RunController componentDidUpdate", state, prevState)
//        val execution = state.execution
        val execution = props.execution
                ?: return

        if (execution.frames.isEmpty()) {
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
        val host = props.documentPath
                ?: return

        ClientContext.executionLoop.pause(host)

        async {
            ClientContext.modelManager.refresh()
            ClientContext.executionManager.reset(host)
        }
    }


    private suspend fun executionStateToFreshStart() {
//        val graphStructure = state.structure
        val graphStructure = props.structure
                ?: return

        val documentPath = props.documentPath
                ?: return

        val expectedDigest = ClientContext.executionManager.start(
                documentPath, graphStructure)

        val actualDigest = ClientContext.restClient.startExecution(documentPath)

//        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())

        if (expectedDigest != actualDigest) {
            // TODO
            console.log("Digest mismatch, refresh required")
            // onRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun phase(): Phase {
//        println("%%%%%% phase - ${state.execution}")
        val host = props.documentPath
                ?: return Phase.Empty

//        val executionModel = state.execution
        val executionModel = props.execution
                ?: return Phase.Empty

        if (executionModel.frames.isEmpty() ||
                executionModel.frames.size == 1 &&
                executionModel.frames[0].states.isEmpty()) {
            return Phase.Empty
        }

        if (executionModel.containsStatus(ExecutionPhase.Running)) {
            if (ClientContext.executionLoop.running(host)) {
                return Phase.Looping
            }
            return Phase.Running
        }

        val next = ImerativeControlFlow.next(props.structure!!.graphNotation, executionModel)
                ?: return Phase.Done

        if (executionModel.containsStatus(ExecutionPhase.Success) ||
                executionModel.containsStatus(ExecutionPhase.Error)) {
            return Phase.Partial
        }

        return Phase.Pending
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onPause() {
        val host = props.documentPath
                ?: return

        ClientContext.executionLoop.pause(host)
    }


    private fun onClear() {
        val host = props.documentPath
                ?: return

        onPause()

        async {
            ClientContext.executionManager.reset(host)
            executionStateToFreshStart()
        }
    }


    private fun onRunAll() {
        val host = props.documentPath
                ?: return

        async {
//            executionStateToFreshStart()

            ClientContext.executionIntent.clear()
            ClientContext.executionLoop.run(host)
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
//        val nextToRun = state.execution?.next()
        val nextToRun = props.execution?.let {
            ImerativeControlFlow.next(props.structure!!.graphNotation, it)
        }
        if (nextToRun == ClientContext.executionIntent.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.set(nextToRun)
        }
    }


    private fun onFabLeave() {
//        val nextToRun = state.execution?.next()
        val nextToRun = props.execution?.let {
            ImerativeControlFlow.next(props.structure!!.graphNotation, it)
        }
//        println("^$%^$%^% onRunAllLeave - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.clearIf(nextToRun)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +"Path: ${props.documentPath?.asString()}"
//        br {}
//        +"Exec: ${state.execution?.frames?.lastOrNull()?.path}"

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
//                                    Color("#ffb82d")
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