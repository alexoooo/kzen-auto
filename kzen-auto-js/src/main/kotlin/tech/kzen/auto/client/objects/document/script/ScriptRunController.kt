package tech.kzen.auto.client.objects.document.script

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.RBuilder
import react.RProps
import react.RState
import react.dom.div
import react.setState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


class ScriptRunController(
        props: Props
):
        RPureComponent<ScriptRunController.Props, ScriptRunController.State>(props),
        ExecutionRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
            var execution: ImperativeModel?
    ): RProps


    class State(
            var fabHover: Boolean
    ): RState


    private enum class Phase {
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
            executionModel: ImperativeModel
    ) {
        if (host != props.documentPath) {
            return
        }

        if (! executionModel.containsStatus(ImperativePhase.Running)) {
            val next = ImperativeUtils.next(props.structure!!, executionModel)
            if (next == null) {
                if (ClientContext.executionLoop.isLooping(host)) {
                    ClientContext.executionLoop.pause(host)
                }

                ClientContext.executionIntentGlobal.clear()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.executionRepository.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionRepository.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
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
//    private fun onRefresh() {
//        val host = props.documentPath
//                ?: return
//
//        ClientContext.executionLoop.pause(host)
//
//        async {
//            ClientContext.modelManager.refresh()
//            ClientContext.executionManager.reset(host)
//        }
//    }


    private suspend fun executionStateToFreshStart() {
//        val graphStructure = state.structure
        val graphStructure = props.structure
                ?: return

        val documentPath = props.documentPath
                ?: return

        val expectedDigest = ClientContext.executionRepository.start(
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

        val looping = ClientContext.executionLoop.isLooping(host)

        if (executionModel.containsStatus(ImperativePhase.Running)) {
            if (looping) {
                return Phase.Looping
            }
            return Phase.Running
        }

        ImperativeUtils.next(props.structure!!, executionModel)
                ?: return Phase.Done

        if (looping) {
            return Phase.Looping
        }

        if (executionModel.containsStatus(ImperativePhase.Success) ||
                executionModel.containsStatus(ImperativePhase.Error)) {
            return Phase.Partial
        }

        return Phase.Pending
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        val nextToRun = ImperativeUtils.next(
                props.structure!!, props.execution!!)!!

        async {
            ClientContext.executionRepository.execute(
                    props.documentPath!!,
                    nextToRun,
                    props.structure!!)
        }
    }


    private fun onRunAll() {
        val host = props.documentPath
                ?: return

        async {
            ClientContext.executionIntentGlobal.clear()
            ClientContext.executionLoop.run(host)
        }
    }


    private fun onPause() {
        val host = props.documentPath
                ?: return

        ClientContext.executionLoop.pause(host)
    }


    private fun onReset() {
        val host = props.documentPath
                ?: return

        onPause()

        async {
            ClientContext.executionRepository.reset(host)
            executionStateToFreshStart()
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


    private fun onRunEnter() {
//        val nextToRun = state.execution?.next()
        val nextToRun = props.execution?.let {
            ImperativeUtils.next(props.structure!!, it)
        }
        if (nextToRun == ClientContext.executionIntentGlobal.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntentGlobal.set(nextToRun)
        }
    }


    private fun onRunLeave() {
//        val nextToRun = state.execution?.next()
        val nextToRun = props.execution?.let {
            ImperativeUtils.next(props.structure!!, it)
        }
//        println("^$%^$%^% onRunAllLeave - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntentGlobal.clearIf(nextToRun)
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

            renderInner(phase)
        }
    }


    private fun RBuilder.renderInner(
            phase: Phase
    ) {
        renderSecondaryActions(phase)

        renderMainAction(phase)
    }


    private fun RBuilder.renderSecondaryActions(
            phase: Phase
    ) {
        val hasReset = phase == Phase.Partial
        child(MaterialIconButton::class) {
            attrs {
                title = "Reset"

                style = reactStyle {
                    if (! state.fabHover || ! hasReset) {
                        visibility = Visibility.hidden
                    }

                    marginRight = (-0.5).em
                }

                onClick = ::onReset
            }

            child(ReplayIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.5.em
                    }
                }
            }
        }

        val hasRunNext = hasReset || phase == Phase.Pending
        child(MaterialIconButton::class) {
            attrs {
                onMouseOver = ::onRunEnter
                onMouseOut = ::onRunLeave

                title = "Run next"

                style = reactStyle {
                    if (! state.fabHover || ! hasRunNext) {
                        visibility = Visibility.hidden
                    }
                }

                onClick = ::onRun
            }

            child(RedoIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.5.em
                    }
                }
            }
        }
    }


    private fun RBuilder.renderMainAction(
            phase: Phase
    ) {
//        +"phase: $phase"

        child(MaterialFab::class) {
            val hasMoreToRun = phase == Phase.Pending || phase == Phase.Partial
            val looping = phase == Phase.Looping

            attrs {
                title = when {
                    phase == Phase.Done ->
                        "Reset"

                    looping ->
                        "Pause"

                    phase == Phase.Pending ->
                        "Run all"

                    else ->
                        "Continue"
                }

                onClick = {
                    when {
                        looping ->
                            onPause()

                        hasMoreToRun ->
                            onRunAll()

                        phase == Phase.Done ->
                            onReset()
                    }
                }

                onMouseOver = ::onRunEnter
                onMouseOut = ::onRunLeave

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

            when {
                looping -> child(PauseIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }

                hasMoreToRun -> child(PlayArrowIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }

                else -> child(ReplayIcon::class) {
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