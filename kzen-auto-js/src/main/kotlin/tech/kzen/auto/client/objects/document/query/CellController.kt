package tech.kzen.auto.client.objects.document.query

import kotlinx.css.em
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class CellController(
        props: Props
):
        RPureComponent<CellController.Props, CellController.State>(props),
        ExecutionIntent.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val cardHorizontalMargin = 1.em
        val arrowSide = cardHorizontalMargin.times(2)
        val ingressLength = arrowSide
        val egressLength = arrowSide.plus(cardHorizontalMargin)
        val cardWidth = 20.em
        val cellWidth = cardWidth.plus(cardHorizontalMargin.times(2))
    }


    class Props(
            var cellDescriptor: CellDescriptor,

            var documentPath: DocumentPath,
            var attributeNesting: AttributeNesting,
            var graphStructure: GraphStructure,
            var visualDataflowModel: VisualDataflowModel,
            var dataflowMatrix: DataflowMatrix,
            var dataflowDag: DataflowDag
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ): RState


    private fun Props.vertexLocation() =
            (cellDescriptor as? VertexDescriptor)?.objectLocation


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false

//        visualVertexModel = props.visualDataflowModel.vertices[props.objectLocation]
//                ?: VisualVertexModel.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntent.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntent.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onExecutionIntent(actionLocation: ObjectLocation?) {
        setState {
            intentToRun = actionLocation == props.vertexLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isVertex(): Boolean {
        return props.vertexLocation() != null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (isVertex()) {
            child(VertexController::class) {
                attrs {
                    cellDescriptor = props.cellDescriptor as VertexDescriptor

                    documentPath = props.documentPath
                    attributeNesting = props.attributeNesting
                    graphStructure = props.graphStructure
                    visualDataflowModel = props.visualDataflowModel
                    dataflowDag = props.dataflowDag
                }
            }
        }
        else {
            child(EdgeController::class) {
                attrs {
                    cellDescriptor = props.cellDescriptor as EdgeDescriptor

                    documentPath = props.documentPath
                    attributeNesting = props.attributeNesting
                    graphStructure = props.graphStructure
                    visualDataflowModel = props.visualDataflowModel
                    dataflowMatrix = props.dataflowMatrix
                }
            }
        }
    }
}