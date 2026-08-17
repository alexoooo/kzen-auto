package tech.kzen.auto.client.objects.document.flow

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em
import web.cssom.plus
import web.cssom.times


//---------------------------------------------------------------------------------------------------------------------
external interface CellControllerProps: Props {
    var attributeController: AttributeEditorManager.Wrapper
    var executionIntentGlobal: ExecutionIntentGlobal
    var mirroredGraphStore: MirroredGraphStore

    var cellDescriptor: CellDescriptor

    var documentPath: DocumentPath
    var attributeNesting: AttributeNesting
    var graphStructure: GraphStructure
    var visualFlowModel: VisualFlowModel
    var flowMatrix: FlowMatrix
    var flowDag: FlowDag

    // Routing, derived once per render by FlowController and threaded through — never re-derived per cell.
    var nextToRun: ObjectLocation?
    var edgeRouting: FlowEdgeRouting
}


//---------------------------------------------------------------------------------------------------------------------
// Stateless pass-through: picks the vertex or edge renderer for its cell. NB: no ExecutionIntentGlobal
// subscription here — VertexController has its own, genuinely-used one; this one's intentToRun was read by
// nothing, so every intent publish re-rendered every cell for nothing.
class CellController(
        props: CellControllerProps
):
        RPureComponent<CellControllerProps, State>(props)
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



    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.cellDescriptor is VertexDescriptor) {
            VertexController::class.react {
                attributeController = props.attributeController
                executionIntentGlobal = props.executionIntentGlobal
                mirroredGraphStore = props.mirroredGraphStore

                cellDescriptor = props.cellDescriptor as VertexDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                graphStructure = props.graphStructure
                visualFlowModel = props.visualFlowModel
                flowMatrix = props.flowMatrix
                flowDag = props.flowDag
                nextToRun = props.nextToRun
            }
        }
        else {
            EdgeController::class.react {
                mirroredGraphStore = props.mirroredGraphStore

                cellDescriptor = props.cellDescriptor as EdgeDescriptor

                documentPath = props.documentPath
                attributeNesting = props.attributeNesting
                visualFlowModel = props.visualFlowModel
                flowMatrix = props.flowMatrix
                flowDag = props.flowDag
                edgeRouting = props.edgeRouting
            }
        }
    }
}