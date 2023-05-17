package tech.kzen.auto.client.objects.document.graph

import web.cssom.NamedColor
import web.cssom.Visibility
import web.cssom.em
import emotion.react.css
import js.core.jso
import mui.material.Fab
import mui.material.IconButton
import react.*
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import kotlin.reflect.KClass


//---------------------------------------------------------------------------------------------------------------------
external interface GraphRunControllerProps: Props {
    var documentPath: DocumentPath?
    var graphStructure: GraphStructure?
    var visualDataflowModel: VisualDataflowModel?
}


external interface GraphRunControllerState: State {
    var fabHover: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class GraphRunController(
        props: GraphRunControllerProps
):
        RPureComponent<GraphRunControllerProps, GraphRunControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private enum class Phase {
        Empty,
        Pending,
        Partial,
        Running,
        Looping,
        Done
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: GraphRunControllerProps,
            prevState: GraphRunControllerState,
            snapshot: Any
    ) {
        if (props.visualDataflowModel != prevProps.visualDataflowModel) {
            // NB: only update executionIntent to next-to-run

            if (ClientContext.executionIntentGlobal.actionLocation() == null) {
                return
            }

            val nextToRun = DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!,
                    props.visualDataflowModel!!)

            if (nextToRun != null) {
                ClientContext.executionIntentGlobal.set(nextToRun)
            }
            else {
                ClientContext.executionIntentGlobal.clear()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
        val nextToRun = props.visualDataflowModel?.let {
            DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!,
                    it)
        }
//        console.log("^$%^$%^% onFabEnter - $nextToRun - ${state.visualDataflowModel}")

        if (nextToRun == ClientContext.executionIntentGlobal.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntentGlobal.set(nextToRun)
        }
    }


    private fun onRunLeave() {
        ClientContext.executionIntentGlobal.clear()
////        val nextToRun = state.execution?.next()
//        val nextToRun = state.visualDataflowModel?.let {
//            DataflowUtils.next(
//                    props.documentPath!!,
//                    props.graphStructure!!.graphNotation,
//                    it)
//        }
////        println("^$%^$%^% onRunAllLeave - $nextToRun")
//        if (nextToRun != null) {
//            ClientContext.executionIntent.clearIf(nextToRun)
//        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    private fun phase(): Phase {
        val host = props.documentPath
                ?: return Phase.Empty

        val visualDataflowModel = props.visualDataflowModel
                ?: return Phase.Empty

        if (visualDataflowModel.vertices.isEmpty()) {
            return Phase.Empty
        }

        // NB: could be stale due to async
        val isLooping = ClientContext.visualDataflowLoop.isLooping(host)

        if (visualDataflowModel.isRunning()) {
            if (isLooping) {
                return Phase.Looping
            }
            return Phase.Running
        }

        val nextVertex = DataflowUtils.next(
                host,
                props.graphStructure!!,
                visualDataflowModel)

        @Suppress("FoldInitializerAndIfToElvis")
        if (nextVertex == null) {
            return Phase.Done
        }

        if (isLooping) {
            return Phase.Looping
        }

        if (visualDataflowModel.isInProgress()) {
            return Phase.Partial
        }

        return Phase.Pending
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        val documentPath = props.documentPath
                ?: return

        val visualDataflowModel = props.visualDataflowModel
                ?: return

        val nextToRun = DataflowUtils.next(
                documentPath,
                props.graphStructure!!,
                visualDataflowModel
        ) ?: return

        async {
            ClientContext.visualDataflowRepository.execute(
                    documentPath,
                    nextToRun,
                    0,
                    200
            )
        }
    }


    private fun onRunAll() {
        val host = props.documentPath
                ?: return

        if (ClientContext.visualDataflowLoop.isLooping(host)) {
            return
        }

        async {
            ClientContext.executionIntentGlobal.clear()
            ClientContext.visualDataflowLoop.loop(host)
        }
    }


    private fun onPause() {
//        console.log("^^^^^^^ onPause")
        val host = props.documentPath
                ?: return

        if (! ClientContext.visualDataflowLoop.isLooping(host)) {
            return
        }

        ClientContext.visualDataflowLoop.pause(host)
    }


    private fun onReset() {
        val host = props.documentPath
                ?: return

        ClientContext.executionIntentGlobal.clear()
        onPause()

        async {
            ClientContext.visualDataflowRepository.reset(host)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val phase = phase()

//        +"phase: $phase"

        if (phase == Phase.Empty) {
            return
        }

        div {
            onMouseOver = {
                onOuterEnter()
            }
            onMouseOut = {
                onOuterLeave()
            }

            renderInner(phase)
        }
    }


    private fun ChildrenBuilder.renderInner(
            phase: Phase
    ) {
        renderSecondaryActions(phase)
        renderMainAction(phase)
    }


    private fun ChildrenBuilder.renderMainAction(
            phase: Phase
    ) {
        val hasMoreToRun = phase == Phase.Pending || phase == Phase.Partial
        val looping = phase == Phase.Looping

        val clickHandle = {
//            console.log("^^^^^!! FAB click - $phase")
            when {
                looping || phase == Phase.Running ->
                    onPause()

                hasMoreToRun ->
                    onRunAll()

                phase == Phase.Done ->
                    onReset()
            }
        }

        Fab {
            onMouseOver = { onRunEnter() }
            onMouseOut = { onRunLeave() }

            title = when {
                phase == Phase.Done ->
                    "Reset"

                looping || phase == Phase.Running ->
                    "Pause"

                phase == Phase.Pending ->
                    "Run all"

                else ->
                    "Run all (continue)"
            }

            onClick = { clickHandle() }

            css {
                backgroundColor =
                    if (hasMoreToRun || looping) {
                        NamedColor.gold
                    }
                    else {
                        NamedColor.white
                    }

                width = 5.em
                height = 5.em
            }

            val icon: KClass<out Component<IconProps, react.State>> = when {
                hasMoreToRun ->
                    PlayArrowIcon::class

                looping ->
                    PauseIcon::class

                else ->
                    ReplayIcon::class
            }

//            styledDiv {
//                css {
//                    backgroundColor = Color.red
//                }
//                attrs {
//                    onClickFunction = {
//                        console.log("&^%&^%&%&^% click !!")
//                    }
//                }
                icon.react {
                    style = jso {
                        fontSize = 3.em
                    }
                }
        }
    }


    private fun ChildrenBuilder.renderSecondaryActions(
            phase: Phase
    ) {
        val hasReset = phase == Phase.Partial
        IconButton {
            title = "Reset"

            css {
                if (! state.fabHover || ! hasReset) {
                    visibility = Visibility.hidden
                }
                marginRight = (-0.5).em
            }

            onClick = { onReset() }

            ReplayIcon::class.react {
                style = jso {
                    fontSize = 1.5.em
                }
            }
        }

        val hasRunNext = phase == Phase.Partial || phase == Phase.Pending
        IconButton {
            onMouseOver = { onRunEnter() }
            onMouseOut = { onRunLeave() }

            title = "Run next"
            css {
                if (! state.fabHover || ! hasRunNext) {
                    visibility = Visibility.hidden
                }
            }

            onClick = { onRun() }

            RedoIcon::class.react {
                style = jso {
                    fontSize = 1.5.em
                }
            }
        }
    }
}