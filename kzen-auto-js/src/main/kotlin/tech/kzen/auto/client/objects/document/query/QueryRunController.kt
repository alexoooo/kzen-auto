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


//    enum class Phase {
//        Empty,
//        Pending,
//        Partial,
//        Running,
//        Looping,
//        Done
//    }


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


    private fun onReset() {
        val host = props.documentPath
                ?: return

        async {
            ClientContext.visualDataflowManager.reset(host)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val isInProgress = state.visualDataflowModel?.isInProgress() ?: false

        val hasNextToRun: Boolean = run block@{
            val documentPath = props.documentPath
                    ?: return@block false

            val graphNotation = props.graphStructure?.graphNotation
                    ?: return@block false

            val visualDataflowModel = state.visualDataflowModel
                    ?: return@block false

            val nextToRun = DataflowUtils.next(
                    documentPath,
                    graphNotation,
                    visualDataflowModel)

            nextToRun != null
        }

        if (! isInProgress && ! hasNextToRun) {
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

            if (isInProgress && hasNextToRun) {
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

                        onClick = ::onReset
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
                attrs {
                    onMouseOver = ::onFabEnter
                    onMouseOut = ::onFabLeave

                    onClick = {
                        if (hasNextToRun) {
                            onRun()
                        }
                        else {
                            onReset()
                        }
                    }

                    style = reactStyle {
                        backgroundColor =
                                if (hasNextToRun) {
                                    Color.gold
                                }
                                else {
                                    Color.white
                                }

                        width = 5.em
                        height = 5.em
                    }
                }

                if (hasNextToRun) {
                    +"Next"
                }
                else {
                    +"Reset"
                }
            }
        }
    }
}