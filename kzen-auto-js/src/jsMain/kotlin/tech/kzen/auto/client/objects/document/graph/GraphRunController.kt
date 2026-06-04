package tech.kzen.auto.client.objects.document.graph

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Fab
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import web.cssom.NamedColor
import web.cssom.Visibility
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface GraphRunControllerProps: Props {
    var documentPath: DocumentPath?
    var graphStructure: GraphStructure?
    var visualDataflowModel: VisualDataflowModel?

    var executionIntentGlobal: ExecutionIntentGlobal
    var visualDataflowRepository: VisualDataflowRepository
    var visualDataflowLoop: VisualDataflowLoop
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

            if (props.executionIntentGlobal.actionLocation() == null) {
                return
            }

            val nextToRun = DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!,
                    props.visualDataflowModel!!)

            if (nextToRun != null) {
                props.executionIntentGlobal.set(nextToRun)
            }
            else {
                props.executionIntentGlobal.clear()
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

        if (nextToRun == props.executionIntentGlobal.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            props.executionIntentGlobal.set(nextToRun)
        }
    }


    private fun onRunLeave() {
        props.executionIntentGlobal.clear()
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
        val isLooping = props.visualDataflowLoop.isLooping(host)

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
            props.visualDataflowRepository.execute(
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

        if (props.visualDataflowLoop.isLooping(host)) {
            return
        }

        async {
            props.executionIntentGlobal.clear()
            props.visualDataflowLoop.loop(host)
        }
    }


    private fun onPause() {
//        console.log("^^^^^^^ onPause")
        val host = props.documentPath
                ?: return

        if (!props.visualDataflowLoop.isLooping(host)) {
            return
        }

        props.visualDataflowLoop.pause(host)
    }


    private fun onReset() {
        val host = props.documentPath
                ?: return

        props.executionIntentGlobal.clear()
        onPause()

        async {
            props.visualDataflowRepository.reset(host)
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

            val iconName = when {
                hasMoreToRun ->
                    "material-symbols:play-arrow"

                looping ->
                    "material-symbols:pause"

                else ->
                    "material-symbols:replay"
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
                icon(iconName) {
                    style = unsafeJso {
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
                if (!state.fabHover || !hasReset) {
                    visibility = Visibility.hidden
                }
                marginRight = (-0.5).em
            }

            onClick = { onReset() }

            icon("material-symbols:replay") {
                style = unsafeJso {
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
                if (!state.fabHover || !hasRunNext) {
                    visibility = Visibility.hidden
                }
            }

            onClick = { onRun() }

            icon("material-symbols:redo") {
                style = unsafeJso {
                    fontSize = 1.5.em
                }
            }
        }
    }
}