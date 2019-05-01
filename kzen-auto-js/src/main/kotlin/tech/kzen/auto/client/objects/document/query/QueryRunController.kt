package tech.kzen.auto.client.objects.document.query

import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.RBuilder
import react.RProps
import react.RState
import react.dom.br
import react.dom.div
import react.setState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialFab
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class QueryRunController(
        props: Props
):
//        RComponent<RunController.Props, RunController.State>(props),
        RPureComponent<QueryRunController.Props, QueryRunController.State>(props),
//        ModelManager.Observer,
        VisualDataflowManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?/*,
            var visualDataflowModel: VisualDataflowModel?*/
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
            DataflowUtils.next(props.graphStructure!!.graphNotation, it)
        }
        console.log("^$%^$%^% onFabEnter - $nextToRun - ${state.visualDataflowModel}")

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
        val nextToRun = state.visualDataflowModel?.let {
            DataflowUtils.next(props.graphStructure!!.graphNotation, it)
        }
//        println("^$%^$%^% onRunAllLeave - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.clearIf(nextToRun)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        div {
            attrs {
                onMouseOverFunction = {
                    onOuterEnter()
                }
                onMouseOutFunction = {
                    onOuterLeave()
                }
            }

            child(MaterialFab::class) {
                attrs {
                    onMouseOver = ::onFabEnter
                    onMouseOut = ::onFabLeave
                }

                +"Next"

                br {}

                +"Reset"
            }
        }
    }
}