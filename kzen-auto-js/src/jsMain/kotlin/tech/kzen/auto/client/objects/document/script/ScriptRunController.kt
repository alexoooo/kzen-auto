package tech.kzen.auto.client.objects.document.script
//
//import emotion.react.css
//import js.core.jso
//import kotlinx.coroutines.delay
//import mui.material.Fab
//import mui.material.IconButton
//import react.ChildrenBuilder
//import react.Props
//import react.dom.html.ReactHTML.div
//import react.react
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.RPureComponent
//import tech.kzen.auto.client.wrap.material.*
//import tech.kzen.auto.client.wrap.setState
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
//import tech.kzen.auto.common.paradigm.imperative.model.control.InitialControlState
//import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
//import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.location.ObjectLocation
//import tech.kzen.lib.common.model.structure.GraphStructure
//import web.cssom.NamedColor
//import web.cssom.Visibility
//import web.cssom.em
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface ScriptRunControllerProps: Props {
//    var documentPath: DocumentPath?
//    var runningHost: DocumentPath?
//    var structure: GraphStructure?
//    var execution: ImperativeModel?
//}
//
//
//external interface ScriptRunControllerState: react.State {
//    var fabHover: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class ScriptRunController(
//        props: ScriptRunControllerProps
//):
//        RPureComponent<ScriptRunControllerProps, ScriptRunControllerState>(props),
//        ExecutionRepository.Observer
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private enum class Phase {
//        Empty,
//        Pending,
//        Partial,
//        Running,
//        Looping,
//        Done
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private var previousControlRun: ObjectLocation? = null
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
//        previousControlRun = null
//
//        val frame = props.execution?.findLast(objectLocation)
//                ?: return
//
//        val control = frame.states[objectLocation.objectPath]?.controlState
//                ?: return
//
//        if (control !is InitialControlState) {
//            return
//        }
//
//        previousControlRun = objectLocation
//    }
//
//
//    override suspend fun onExecutionModel(
//            host: DocumentPath,
//            executionModel: ImperativeModel?
//    ) {
////        console.log("^^^ onExecutionModel: $host - $executionModel")
//
//        if (host != props.documentPath &&
//                executionModel?.frames?.find { it.path == props.documentPath} == null)
//        {
//            return
//        }
//
//        if (executionModel != null &&
//                ! executionModel.containsStatus(ImperativePhase.Running))
//        {
//            val next = ImperativeUtils.next(props.structure!!, executionModel)
//            if (next == null) {
//                if (ClientContext.executionLoop.isLooping(host)) {
//                    val nested = props.execution?.frames?.size ?: 0 > 1
//                    if (nested) {
//                        ClientContext.executionLoop.returnFrame(host)
//                        onReturn()
//                    }
//                    else {
//                        ClientContext.executionLoop.pause(host)
//                    }
//                }
//
//                ClientContext.executionIntentGlobal.clear()
//            }
//        }
//
//        if (previousControlRun != null &&
//                executionModel != null &&
//                ! executionModel.isRunning() &&
//                executionModel.frames.size > 1 &&
//                ! executionModel.frames.last().isActive(null))
//        {
////            console.log("^^^^ ENTERED: " + executionModel.frames.last().path.asString())
//            async {
//                delay(1)
//
//                ClientContext.navigationGlobal.goto(
//                        executionModel.frames.last().path)
//
////                ClientContext.navigationGlobal.parameterize(RequestParams(
////                        mapOf(RibbonRun.runningKey to listOf(
////                                executionModel.frames.last().path.asString()))
////                ))
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun componentDidMount() {
//        async {
//            ClientContext.executionRepository.observe(this)
//        }
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
//    }
//
//
//    override fun componentDidUpdate(
//            prevProps: ScriptRunControllerProps,
//            prevState: ScriptRunControllerState,
//            snapshot: Any
//    ) {
////        val execution = props.execution
////                ?: return
////
////        if (execution.frames.isEmpty()) {
////            async {
////                executionStateToFreshStart()
////            }
////            return
////        }
//
//        val runningHost = props.runningHost
//
////        console.log("!@#!#!@#!@#!@  componentDidUpdate - " +
////                "${props.documentPath} - ${prevProps.documentPath} - $runningHost - " +
////                "${runningHost?.let {ClientContext.executionLoop.isContinuingFrame(it)}}")
//
//        if (props.documentPath != prevProps.documentPath &&
//                runningHost != null &&
//                ClientContext.executionLoop.isContinuingFrame(runningHost))
//        {
////            console.log("^^^^^^^^^ continuing execution")
//            async {
//                ClientContext.executionLoop.continueFrame(runningHost)
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
////    private suspend fun executionStateToFreshStart() {
//////        val graphStructure = state.structure
////        val graphStructure = props.structure
////                ?: return
////
////        val documentPath = props.documentPath
////                ?: return
////
////        val expectedDigest = ClientContext.executionRepository.start(
////                documentPath, graphStructure)
////
////        val actualDigest = ClientContext.restClient.startExecution(documentPath)
////
//////        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())
////
////        if (expectedDigest != actualDigest) {
////            // TODO
////            console.log("Digest mismatch, refresh required")
////            // onRefresh()
////        }
////    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun phase(): Phase {
////        println("%%%%%% phase - ${state.execution}")
//        val host = props.documentPath
//                ?: return Phase.Empty
//
////        val executionModel = state.execution
//        val executionModel = props.execution
//                ?: return Phase.Empty
//
//        if (executionModel.frames.isEmpty() ||
//                executionModel.frames.size == 1 &&
//                executionModel.frames[0].states.isEmpty()) {
//            return Phase.Empty
//        }
//
//        val looping = ClientContext.executionLoop.isLooping(host)
//
//        if (executionModel.containsStatus(ImperativePhase.Running)) {
//            if (looping) {
//                return Phase.Looping
//            }
//            return Phase.Running
//        }
//
//        ImperativeUtils.next(props.structure!!, executionModel)
//                ?: return Phase.Done
//
//        if (looping) {
//            return Phase.Looping
//        }
//
//        if (executionModel.containsStatus(ImperativePhase.Success) ||
//                executionModel.containsStatus(ImperativePhase.Error)) {
//            return Phase.Partial
//        }
//
//        return Phase.Pending
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private suspend fun onRun() {
//        val graphStructure = props.structure!!
//        val imperativeModel = props.execution!!
//
//        if (! imperativeModel.isActive()) {
//            ClientContext.restClient.startExecution(props.documentPath!!)
//        }
//
//        val nextToRun = ImperativeUtils.next(
//                graphStructure, imperativeModel)!!
//
//        async {
//            ClientContext.executionRepository.execute(
//                    imperativeModel.frames.first().path,
//                    nextToRun,
//                    graphStructure)
//        }
//    }
//
//
//    private fun onReturn() {
//        val graphStructure = props.structure!!
//        val imperativeModel = props.execution!!
//
////        console.log("&&%^&^% returning frame")
//
//        async {
//            val previousFrame =
//                    if (imperativeModel.frames.size > 1) {
//                        imperativeModel.frames[imperativeModel.frames.size - 2].path
//                    }
//                    else {
//                        null
//                    }
//
////            console.log("&&%^&^% returning frame - async")
//
//            ClientContext.executionRepository.returnFrame(
//                    imperativeModel.frames.first().path, graphStructure)
//
//            if (previousFrame != null) {
//                ClientContext.navigationGlobal.returnTo(previousFrame)
//            }
//        }
//    }
//
//
//    private fun onRunAll() {
//        val host = props.documentPath
//                ?: return
//
//        async {
//            ClientContext.executionIntentGlobal.clear()
//
//            if (props.execution?.isActive() != true) {
//                ClientContext.executionRepository.start(
//                        host, props.structure!!)
//            }
//
//            ClientContext.executionLoop.run(host)
//        }
//    }
//
//
//    private fun onPause() {
//        val host = props.documentPath
//                ?: return
//
//        ClientContext.executionLoop.pause(host)
//    }
//
//
//    private fun onReset() {
//        val host = props.documentPath
//                ?: return
//
//        onPause()
//
//        async {
//            ClientContext.executionRepository.reset(host)
//            ClientContext.restClient.resetExecution(host)
//        }
//    }
//
//
//    private fun onOuterEnter() {
//        setState {
//            fabHover = true
//        }
//    }
//
//
//    private fun onOuterLeave() {
//        setState {
//            fabHover = false
//        }
//    }
//
//
//    private fun onRunEnter() {
////        val nextToRun = state.execution?.next()
//        val nextToRun = props.execution?.let {
//            ImperativeUtils.next(props.structure!!, it)
//        }
//        if (nextToRun == ClientContext.executionIntentGlobal.actionLocation()) {
//            return
//        }
//
////        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
//        if (nextToRun != null) {
//            ClientContext.executionIntentGlobal.set(nextToRun)
//        }
//    }
//
//
//    private fun onRunLeave() {
////        val nextToRun = state.execution?.next()
//        val nextToRun = props.execution?.let {
//            ImperativeUtils.next(props.structure!!, it)
//        }
////        println("^$%^$%^% onRunAllLeave - $nextToRun")
//        if (nextToRun != null) {
//            ClientContext.executionIntentGlobal.clearIf(nextToRun)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun ChildrenBuilder.render() {
//        if (props.runningHost != null &&
//                props.execution != null &&
//                props.execution!!.frames.none { it.path == props.documentPath })
//        {
////            +"[Running \"${props.runningHost!!.name.value}\"]"
//            return
//        }
//
////        +"Path: ${props.documentPath?.asString()}"
////        br {}
////        +"Exec: ${state.execution?.frames?.lastOrNull()?.path}"
//
//        val phase = phase()
////        println("#$#%#$%#$% phase - $phase")
//
//        if (phase == Phase.Empty) {
//            return
//        }
//
//        div {
//            onMouseOver = {
//                onOuterEnter()
//            }
//            onMouseOut = {
//                onOuterLeave()
//            }
//
//            renderInner(phase)
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderInner(
//            phase: Phase
//    ) {
//        val nested =
//                props.execution?.frames?.size ?: 0 > 1
//
//        renderSecondaryActions(phase)
//
//        renderMainAction(phase, nested)
//    }
//
//
//    private fun ChildrenBuilder.renderSecondaryActions(
//            phase: Phase
//    ) {
//        val hasReset = phase == Phase.Partial
//        IconButton {
//            title = "Reset"
//
//            css {
//                if (! state.fabHover || ! hasReset) {
//                    visibility = Visibility.hidden
//                }
//
//                marginRight = (-0.5).em
//            }
//
//            onClick = {
//                onReset()
//            }
//
//            ReplayIcon::class.react {
//                style = jso {
//                    fontSize = 1.5.em
//                }
//            }
//        }
//
//        val hasRunNext = hasReset || phase == Phase.Pending
//        IconButton {
//            onMouseOver = { onRunEnter() }
//            onMouseOut = { onRunLeave() }
//
//            title = "Run next"
//            css {
//                if (! state.fabHover || ! hasRunNext) {
//                    visibility = Visibility.hidden
//                }
//            }
//
//            onClick = {
//                async {
//                    onRun()
//                }
//            }
//
//            RedoIcon::class.react {
//                style = jso {
//                    fontSize = 1.5.em
//                }
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderMainAction(
//            phase: Phase,
//            nested: Boolean
//    ) {
//        Fab {
//            val hasMoreToRun = phase == Phase.Pending || phase == Phase.Partial
//            val looping = phase == Phase.Looping
//
//            title = when {
//                phase == Phase.Done ->
//                    if (nested) {
//                        "Continue"
//                    }
//                    else {
//                        "Reset"
//                    }
//
//                looping ->
//                    "Pause"
//
//                phase == Phase.Pending ->
//                    "Run all"
//
//                else ->
//                    "Continue"
//            }
//
//            onClick = {
//                when {
//                    looping ->
//                        onPause()
//
//                    hasMoreToRun ->
//                        onRunAll()
//
//                    phase == Phase.Done ->
//                        if (nested) {
//                            onReturn()
//                        }
//                        else {
//                            onReset()
//                        }
//                }
//            }
//
//            onMouseOver = { onRunEnter() }
//            onMouseOut = { onRunLeave() }
//
//            css {
//                backgroundColor =
//                    if (hasMoreToRun || looping) {
//                        NamedColor.gold
//                    }
//                    else {
//                        NamedColor.white
//                    }
//
//                width = 5.em
//                height = 5.em
//            }
//
//            when {
//                looping -> PauseIcon::class.react {
//                    style = jso {
//                        fontSize = 3.em
//                    }
//                }
//
//                hasMoreToRun -> PlayArrowIcon::class.react {
//                    style = jso {
//                        fontSize = 3.em
//                    }
//                }
//
//                nested -> KeyboardReturnIcon::class.react {
//                    style = jso {
//                        fontSize = 3.em
//                    }
//                }
//
//                else -> ReplayIcon::class.react {
//                    style = jso {
//                        fontSize = 3.em
//                    }
//                }
//            }
//        }
//    }
//}