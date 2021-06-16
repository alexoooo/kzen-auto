package tech.kzen.auto.client.objects.document.script

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.attrs
import react.dom.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.control.InitialControlState
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
            var runningHost: DocumentPath?,
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
    private var previousControlRun: ObjectLocation? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
        previousControlRun = null

        val frame = props.execution?.findLast(objectLocation)
                ?: return

        val control = frame.states[objectLocation.objectPath]?.controlState
                ?: return

        if (control !is InitialControlState) {
            return
        }

        previousControlRun = objectLocation
    }


    override suspend fun onExecutionModel(
            host: DocumentPath,
            executionModel: ImperativeModel?
    ) {
//        console.log("^^^ onExecutionModel: $host - $executionModel")

        if (host != props.documentPath &&
                executionModel?.frames?.find { it.path == props.documentPath} == null)
        {
            return
        }

        if (executionModel != null &&
                ! executionModel.containsStatus(ImperativePhase.Running))
        {
            val next = ImperativeUtils.next(props.structure!!, executionModel)
            if (next == null) {
                if (ClientContext.executionLoop.isLooping(host)) {
                    val nested = props.execution?.frames?.size ?: 0 > 1
                    if (nested) {
                        ClientContext.executionLoop.returnFrame(host)
                        onReturn()
                    }
                    else {
                        ClientContext.executionLoop.pause(host)
                    }
                }

                ClientContext.executionIntentGlobal.clear()
            }
        }

        if (previousControlRun != null &&
                executionModel != null &&
                ! executionModel.isRunning() &&
                executionModel.frames.size > 1 &&
                ! executionModel.frames.last().isActive(null))
        {
//            console.log("^^^^ ENTERED: " + executionModel.frames.last().path.asString())
            async {
                delay(1)

                ClientContext.navigationGlobal.goto(
                        executionModel.frames.last().path)

//                ClientContext.navigationGlobal.parameterize(RequestParams(
//                        mapOf(RibbonRun.runningKey to listOf(
//                                executionModel.frames.last().path.asString()))
//                ))
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
//        val execution = props.execution
//                ?: return
//
//        if (execution.frames.isEmpty()) {
//            async {
//                executionStateToFreshStart()
//            }
//            return
//        }

        val runningHost = props.runningHost

//        console.log("!@#!#!@#!@#!@  componentDidUpdate - " +
//                "${props.documentPath} - ${prevProps.documentPath} - $runningHost - " +
//                "${runningHost?.let {ClientContext.executionLoop.isContinuingFrame(it)}}")

        if (props.documentPath != prevProps.documentPath &&
                runningHost != null &&
                ClientContext.executionLoop.isContinuingFrame(runningHost))
        {
//            console.log("^^^^^^^^^ continuing execution")
            async {
                ClientContext.executionLoop.continueFrame(runningHost)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private suspend fun executionStateToFreshStart() {
////        val graphStructure = state.structure
//        val graphStructure = props.structure
//                ?: return
//
//        val documentPath = props.documentPath
//                ?: return
//
//        val expectedDigest = ClientContext.executionRepository.start(
//                documentPath, graphStructure)
//
//        val actualDigest = ClientContext.restClient.startExecution(documentPath)
//
////        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())
//
//        if (expectedDigest != actualDigest) {
//            // TODO
//            console.log("Digest mismatch, refresh required")
//            // onRefresh()
//        }
//    }


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
    private suspend fun onRun() {
        val graphStructure = props.structure!!
        val imperativeModel = props.execution!!

        if (! imperativeModel.isActive()) {
            ClientContext.restClient.startExecution(props.documentPath!!)
        }

        val nextToRun = ImperativeUtils.next(
                graphStructure, imperativeModel)!!

        async {
            ClientContext.executionRepository.execute(
                    imperativeModel.frames.first().path,
                    nextToRun,
                    graphStructure)
        }
    }


    private fun onReturn() {
        val graphStructure = props.structure!!
        val imperativeModel = props.execution!!

//        console.log("&&%^&^% returning frame")

        async {
            val previousFrame =
                    if (imperativeModel.frames.size > 1) {
                        imperativeModel.frames[imperativeModel.frames.size - 2].path
                    }
                    else {
                        null
                    }

//            console.log("&&%^&^% returning frame - async")

            ClientContext.executionRepository.returnFrame(
                    imperativeModel.frames.first().path, graphStructure)

            if (previousFrame != null) {
                ClientContext.navigationGlobal.returnTo(previousFrame)
            }
        }
    }


    private fun onRunAll() {
        val host = props.documentPath
                ?: return

        async {
            ClientContext.executionIntentGlobal.clear()

            if (props.execution?.isActive() != true) {
                ClientContext.executionRepository.start(
                        host, props.structure!!)
            }

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
            ClientContext.restClient.resetExecution(host)
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
        if (props.runningHost != null &&
                props.execution != null &&
                props.execution!!.frames.none { it.path == props.documentPath })
        {
//            +"[Running \"${props.runningHost!!.name.value}\"]"
            return
        }

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
        val nested =
                props.execution?.frames?.size ?: 0 > 1

        renderSecondaryActions(phase)

        renderMainAction(phase, nested)
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

                onClick = {
                    async {
                        onRun()
                    }
                }
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
            phase: Phase,
            nested: Boolean
    ) {
//        +"phase: $phase"

        child(MaterialFab::class) {
            val hasMoreToRun = phase == Phase.Pending || phase == Phase.Partial
            val looping = phase == Phase.Looping

            attrs {
                title = when {
                    phase == Phase.Done ->
                        if (nested) {
                            "Continue"
                        }
                        else {
                            "Reset"
                        }

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
                            if (nested) {
                                onReturn()
                            }
                            else {
                                onReset()
                            }
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

                nested -> child(KeyboardReturnIcon::class) {
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