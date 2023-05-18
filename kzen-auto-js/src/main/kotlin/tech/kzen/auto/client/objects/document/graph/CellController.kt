package tech.kzen.auto.client.objects.document.graph

import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import web.cssom.em
import web.cssom.plus
import web.cssom.times


//---------------------------------------------------------------------------------------------------------------------
external interface CellControllerProps: Props {
    var attributeController: AttributeController.Wrapper

    var cellDescriptor: CellDescriptor

    var documentPath: DocumentPath
    var attributeNesting: AttributeNesting
    var clientState: SessionState
    var visualDataflowModel: VisualDataflowModel
    var dataflowMatrix: DataflowMatrix
    var dataflowDag: DataflowDag
}


external interface CellControllerState: State {
    var hoverCard: Boolean
    var hoverMenu: Boolean
    var intentToRun: Boolean

    var optionsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class CellController(
        props: CellControllerProps
):
        RPureComponent<CellControllerProps, CellControllerState>(props),
        ExecutionIntentGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val cardHorizontalMargin = 1.em
        val arrowSide = 2.times(cardHorizontalMargin)
        val ingressLength = arrowSide
        val egressLength = arrowSide.plus(cardHorizontalMargin)
        val cardWidth = 20.em
        val cellWidth = cardWidth.plus(2.times(cardHorizontalMargin))
    }



    private fun CellControllerProps.vertexLocation() =
            (cellDescriptor as? VertexDescriptor)?.objectLocation


    //-----------------------------------------------------------------------------------------------------------------
    override fun CellControllerState.init(props: CellControllerProps) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false

//        visualVertexModel = props.visualDataflowModel.vertices[props.objectLocation]
//                ?: VisualVertexModel.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntentGlobal.unobserve(this)
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
    override fun ChildrenBuilder.render() {
        if (isVertex()) {
            VertexController::class.react {
                attributeController = props.attributeController

                cellDescriptor = props.cellDescriptor as VertexDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                clientState = props.clientState
                visualDataflowModel = props.visualDataflowModel
                dataflowMatrix = props.dataflowMatrix
                dataflowDag = props.dataflowDag
            }
        }
        else {
            EdgeController::class.react {
                cellDescriptor = props.cellDescriptor as EdgeDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                graphStructure = props.clientState.graphStructure()
                visualDataflowModel = props.visualDataflowModel
                dataflowMatrix = props.dataflowMatrix
                dataflowDag = props.dataflowDag
            }
        }
    }
}