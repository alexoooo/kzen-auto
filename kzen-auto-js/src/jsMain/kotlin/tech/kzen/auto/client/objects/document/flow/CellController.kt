package tech.kzen.auto.client.objects.document.flow

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.flow.edit.AttributeEditorManagerOld
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em
import web.cssom.plus
import web.cssom.times


//---------------------------------------------------------------------------------------------------------------------
external interface CellControllerProps: Props {
    var attributeController: AttributeEditorManagerOld.Wrapper
    var executionIntentGlobal: ExecutionIntentGlobal
    var mirroredGraphStore: MirroredGraphStore

    var cellDescriptor: CellDescriptor

    var documentPath: DocumentPath
    var attributeNesting: AttributeNesting
    var clientState: ClientState
    var visualFlowModel: VisualFlowModel
    var flowMatrix: FlowMatrix
    var flowDag: FlowDag
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

//        visualVertexModel = props.visualFlowModel.vertices[props.objectLocation]
//                ?: VisualVertexModel.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.executionIntentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.executionIntentGlobal.unobserve(this)
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
                executionIntentGlobal = props.executionIntentGlobal
                mirroredGraphStore = props.mirroredGraphStore

                cellDescriptor = props.cellDescriptor as VertexDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                clientState = props.clientState
                visualFlowModel = props.visualFlowModel
                flowMatrix = props.flowMatrix
                flowDag = props.flowDag
            }
        }
        else {
            EdgeController::class.react {
                mirroredGraphStore = props.mirroredGraphStore

                cellDescriptor = props.cellDescriptor as EdgeDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                graphStructure = props.clientState.graphStructure()
                visualFlowModel = props.visualFlowModel
                flowMatrix = props.flowMatrix
                flowDag = props.flowDag
            }
        }
    }
}