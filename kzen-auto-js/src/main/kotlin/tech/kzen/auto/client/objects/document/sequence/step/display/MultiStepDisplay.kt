package tech.kzen.auto.client.objects.document.sequence.step.display

import emotion.react.css
import js.core.jso
import mui.material.IconButton
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.sequence.SequenceController.Companion.stepLocations
import tech.kzen.auto.client.objects.document.sequence.step.StepDisplayManager
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface MultiStepDisplayProps: SequenceStepDisplayProps {
    var stepDisplayManager: StepDisplayManager.Handle?
//    var attributeController: AttributeController.Wrapper
}


external interface MultiStepDisplayState: State {
    var documentPath: DocumentPath
    var stepLocations: List<ObjectLocation>?

    var creating: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class MultiStepDisplay(
    props: MultiStepDisplayProps
):
    RPureComponent<MultiStepDisplayProps, MultiStepDisplayState>(props),
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val stepDisplayManager: StepDisplayManager.Handle
    ):
        SequenceStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: SequenceStepDisplayProps.() -> Unit) {
            MultiStepDisplay::class.react {
                stepDisplayManager = this@Wrapper.stepDisplayManager
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun onClientState(clientState: SessionState) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure
        val documentPath: DocumentPath = clientState.navigationRoute.documentPath!!

        val stepLocations = stepLocations(graphStructure, documentPath)

        setState {
            this.documentPath = documentPath
            this.stepLocations = stepLocations
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
        println("onCreate($index)")
//        val clientState = state.clientState!!
//
//        val archetypeObjectLocation = ClientContext.insertionGlobal
//            .getAndClearSelection()
//            ?: return
//
//        val documentPath = clientState.navigationRoute.documentPath!!
//        val containingObjectLocation = ObjectLocation(
//            documentPath, NotationConventions.mainObjectPath)
//
//        val commands = props.sequenceCommander.createCommands(
//            containingObjectLocation,
//            SequenceConventions.stepsAttributePath,
//            index,
//            archetypeObjectLocation,
//            clientState.graphDefinitionAttempt.graphStructure
//        )
//
//        async {
//            for (command in commands) {
//                ClientContext.mirroredGraphStore.apply(command)
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
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
                    +"Empty script, please add steps from the toolbar (above)"
                }

                insertionPoint(0)
            }
        }
        else {
            div {
                css {
                    paddingLeft = 1.em
                }
                nonEmptySteps(state.documentPath, stepLocations)
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.nonEmptySteps(
            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>
    ) {
        insertionPoint(0)

        div {
            css {
                width = StepController.width
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                val objectPath = stepLocation.objectPath

                val keyLocation = ObjectLocation(documentPath, objectPath)

                renderStep(
                    index,
                    keyLocation,
                    stepLocations.size)

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

                    marginTop =  0.5.em
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
                    if (! state.creating) {
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

//            +"[Step $index - $stepCount - $objectLocation - ${props.stepDisplayManager}]"

            props.stepDisplayManager?.wrapper?.child(this) {
                common = SequenceStepDisplayPropsCommon(
                    objectLocation,
                    AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
                    props.common.managed,
                    first = index == 0,
                    last = index == stepCount - 1,
                    props.common.sequenceStore)
            }
        }
    }
}