package tech.kzen.auto.client.objects.document.query

import kotlinx.css.Color
import kotlinx.css.Visibility
import kotlinx.css.em
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
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class QueryRunController(
        props: Props
):
        RPureComponent<QueryRunController.Props, QueryRunController.State>(props),
        VisualDataflowManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?//,
//            var
    ): RProps


    class State(
            var visualDataflowModel: VisualDataflowModel?,
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
    override fun componentDidMount() {
//        val documentPath = props.documentPath
//                ?: return

        async {
            ClientContext.visualDataflowManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.visualDataflowManager.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeDataflowExecution(host: DocumentPath, vertexLocation: ObjectLocation) {}


    override suspend fun onVisualDataflowModel(host: DocumentPath, visualDataflowModel: VisualDataflowModel) {
        setState {
            this.visualDataflowModel = visualDataflowModel
        }

        if (ClientContext.executionIntent.actionLocation() != null) {
            val nextToRun = DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!.graphNotation,
                    visualDataflowModel)

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


    private fun onFabEnter() {
        val nextToRun = state.visualDataflowModel?.let {
            DataflowUtils.next(
                    props.documentPath!!,
                    props.graphStructure!!.graphNotation,
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


    private fun onFabLeave() {
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

        val visualDataflowModel = state.visualDataflowModel
                ?: return Phase.Empty

        if (visualDataflowModel.vertices.isEmpty()) {
            return Phase.Empty
        }

        if (visualDataflowModel.isRunning()) {
            if (ClientContext.visualDataflowLoop.running(host)) {
                return Phase.Looping
            }
            return Phase.Running
        }

        val nextVertex = DataflowUtils.next(
                host,
                props.graphStructure!!.graphNotation,
                visualDataflowModel)

        @Suppress("FoldInitializerAndIfToElvis")
        if (nextVertex == null) {
            return Phase.Done
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

        val visualDataflowModel = state.visualDataflowModel
                ?: return

        val nextToRun = DataflowUtils.next(
                documentPath,
                props.graphStructure!!.graphNotation,
                visualDataflowModel
        ) ?: return

        async {
            ClientContext.visualDataflowManager.execute(
                    documentPath,
                    nextToRun,
                    250
            )
        }
    }


    private fun onRunAll() {
        val host = props.documentPath
                ?: return

        async {
            ClientContext.executionIntent.clear()
            ClientContext.visualDataflowLoop.loop(host)
        }
    }


    private fun onPause() {
        val host = props.documentPath
                ?: return

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

        if (phase == Phase.Empty) {
            return
        }

//        +"phase: $phase"

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

        child(MaterialFab::class) {
            attrs {
                onMouseOver = ::onFabEnter
                onMouseOut = ::onFabLeave

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


                onClick = {
                    when {
                        looping || phase == Phase.Running ->
                            onPause()

                        hasMoreToRun ->
                            onRunAll()

                        phase == Phase.Done ->
                            onReset()
                    }
                }

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

            when {
                hasMoreToRun -> child(PlayArrowIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }

                looping -> child(PauseIcon::class) {
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
                title = "Run next"

                style = reactStyle {
                    if (! state.fabHover || ! hasRunNext) {
                        visibility = Visibility.hidden
                    }

//                    marginRight = (-0.5).em
//                    marginRight = (-0.1).em
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