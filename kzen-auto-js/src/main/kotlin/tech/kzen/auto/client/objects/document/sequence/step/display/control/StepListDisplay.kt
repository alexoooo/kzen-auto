package tech.kzen.auto.client.objects.document.sequence.step.display.control

import emotion.react.css
import js.core.jso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.sequence.SequenceController
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.step.StepDisplayManager
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayPropsCommon
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepListDisplayProps: Props {
    var attributeLocation: AttributeLocation
    var nested: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var sequenceCommander: SequenceCommander
}


external interface StepListDisplayState: State {
//    var documentPath: DocumentPath
    var stepLocations: List<ObjectLocation>?

    var creating: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class StepListDisplay(
    props: StepListDisplayProps
):
    RPureComponent<StepListDisplayProps, StepListDisplayState>(props),
    SessionGlobal.Observer,
    InsertionGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun onClientState(clientState: SessionState) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure

        if (props.attributeLocation.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val stepLocations = SequenceController.stepLocations(
            graphStructure, props.attributeLocation)

        setState {
//            this.documentPath = documentPath
            this.stepLocations = stepLocations
        }
    }


    override fun onInsertionSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onInsertionUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
//        println("onCreate(${props.} $index)")

        val graphStructure = ClientContext.sessionGlobal.current()?.graphStructure()
            ?: return

        val archetypeObjectLocation = ClientContext.insertionGlobal
            .getAndClearSelection()
            ?: return

//        val documentPath = state.documentPath
//        val containingObjectLocation = ObjectLocation(
//            documentPath, NotationConventions.mainObjectPath)

        val commands = props.sequenceCommander.createCommands(
            props.attributeLocation,
            index,
            archetypeObjectLocation,
            graphStructure
        )

        async {
            for (command in commands) {
                ClientContext.mirroredGraphStore.apply(command)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        +"[StepListDisplay]"
        val stepLocations = state.stepLocations
            ?: return

        if (stepLocations.isEmpty()) {
            div {
                css {
                    paddingTop = 2.em
                }

                div {
                    css {
                        fontSize = 1.5.em
                    }

                    if (props.nested) {
                        +"Add steps from the toolbar (above)"
                    }
                    else {
                        +"Empty script, please add steps from the toolbar (above)"
                    }
                }

                insertionPoint(0)
            }
        }
        else {
            div {
//                css {
//                    paddingLeft = 1.em
//                }

                nonEmptySteps(stepLocations)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.nonEmptySteps(
//        documentPath: DocumentPath,
        stepLocations: List<ObjectLocation>
    ) {
        insertionPoint(0)

        div {
            css {
                width = StepController.width
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
//                val objectPath = stepLocation.objectPath
//                val keyLocation = ObjectLocation(documentPath, objectPath)

                renderStep(
                    index,
//                    keyLocation,
                    stepLocation,
                    stepLocations.size
                )

                if (index < stepLocations.size - 1) {
                    downArrowWithInsertionPoint(index + 1)
                }
            }
        }

        insertionPoint(stepLocations.size)
    }


    private fun ChildrenBuilder.downArrowWithInsertionPoint(index: Int) {
        div {
            css {
                position = Position.relative
                height = 4.em
                width = StepController.width.div(2).minus(1.em)
            }

            div {
                css {
                    marginTop = 0.5.em

                    position = Position.absolute
                    height = 1.em
                    width = 1.em
                    top = 0.em
                    left = 0.em
                }
                insertionPoint(index)
            }

            div {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = StepController.width.div(2).minus(1.5.em)

                    marginTop = 0.5.em
                    marginBottom = 0.5.em
                }

                ArrowDownwardIcon::class.react {
                    style = jso {
                        fontSize = 3.em
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.insertionPoint(index: Int) {
        span {
            if (state.creating) {
                title = "Insert step here"
            }

            IconButton {
                css {
                    if (!state.creating) {
                        opacity = number(0.0)
                        cursor = Cursor.default
                    }
                }

                onClick = {
                    onCreate(index)
                }

                AddCircleOutlineIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderStep(
        index: Int,
        objectLocation: ObjectLocation,
        stepCount: Int
    ) {
        span {
            key = objectLocation.toReference().asString()

//            +"[Step $index - $stepCount - $objectLocation]"
            props.stepDisplayManager.child(this) {
                common = SequenceStepDisplayPropsCommon(
                    objectLocation,
                    index,
                    first = index == 0,
                    last = index == stepCount - 1,
//                    props.common.sequenceStore
                )
            }
        }
    }
}