package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.GraphStructure
import kotlin.reflect.KClass


class QueryRunController(
        props: Props
):
        RPureComponent<QueryRunController.Props, QueryRunController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?,
            var visualDataflowModel: VisualDataflowModel?
    ): RProps


    class State(
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
    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        if (props.visualDataflowModel != prevProps.visualDataflowModel) {
            // NB: only update executionIntent to next-to-run

            if (ClientContext.executionIntent.actionLocation() == null) {
                return
            }

            val nextToRun = DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!,
                    props.visualDataflowModel!!)

            if (nextToRun != null) {
                ClientContext.executionIntent.set(nextToRun)
            }
            else {
                ClientContext.executionIntent.clear()
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

        if (nextToRun == ClientContext.executionIntent.actionLocation()) {
            return
        }

//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.set(nextToRun)
        }
    }


    private fun onRunLeave() {
        ClientContext.executionIntent.clear()
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
            ClientContext.visualDataflowManager.execute(
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
            ClientContext.executionIntent.clear()
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

        ClientContext.executionIntent.clear()
        onPause()

        async {
            ClientContext.visualDataflowManager.reset(host)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val phase = phase()

//        +"phase: $phase"

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


    private fun RBuilder.renderMainAction(
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

        child(MaterialFab::class) {
            attrs {
                onMouseOver = ::onRunEnter
                onMouseOut = ::onRunLeave

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

                onClick = clickHandle

                style = reactStyle {
                    backgroundColor =
                            if (hasMoreToRun || looping) {
                                Color.gold
                            }
                            else {
                                Color.white
                            }

                    width = 5.em
                    height = 5.em
                }
            }

            val icon: KClass<out Component<IconProps, RState>> = when {
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
                child(icon) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }

//                    onClick = clickHandle
//                        onClick = {
//                            console.log("&^%&^%&%&^% click")
//                        }
                    }
                }
//            }
        }
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

        val hasRunNext = phase == Phase.Partial || phase == Phase.Pending
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
}