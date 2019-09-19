package tech.kzen.auto.client.objects.document.script.step.display

import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RState
import react.dom.span
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.platform.collect.persistentListOf


class ConditionalBranchDisplay(
        props: Props
):
        RPureComponent<
                ConditionalBranchDisplay.Props,
                ConditionalBranchDisplay.State
                >(props),
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun branchLocations(
                stepLocation: ObjectLocation,
                branchAttributePath: AttributePath,
                graphStructure: GraphStructure
        ): List<ObjectLocation>? {
            val branchNotations = graphStructure
                    .graphNotation
                    .transitiveAttribute(stepLocation, branchAttributePath)
                    as? ListAttributeNotation
                    ?: return null

            return branchNotations
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var branchAttributePath: AttributePath,
            var stepController: StepController.Wrapper,
            var graphStructure: GraphStructure,
            var objectLocation: ObjectLocation,
            var imperativeModel: ImperativeModel
    ): RProps


    class State(
            var creating: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        creating = false
    }


    override fun componentDidMount() {
//        console.log("^^^^^^ script - componentDidMount")

//        println("ProjectController - Subscribed")
        async {
            //            console.log("^^^^^^ script - adding observers")
//            ClientContext.modelManager.observe(this)
//            ClientContext.executionManager.observe(this)
            ClientContext.insertionManager.subscribe(this)
//            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        console.log("^^^^^^ script - componentWillUnmount")

//        println("ProjectController - Un-subscribed")
//        ClientContext.modelManager.unobserve(this)
//        ClientContext.executionManager.unobserve(this)
        ClientContext.insertionManager.unSubscribe(this)
//        ClientContext.navigationManager.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
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
        val archetypeObjectLocation = ClientContext.insertionManager
                .getAndClearSelection()
                ?: return

        val command = ScriptDocument.createCommand(
                props.objectLocation,
                props.branchAttributePath,
                index,
                archetypeObjectLocation,
                props.graphStructure
        )

        async {
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                marginLeft = 2.em
            }

            renderSteps()
        }
    }


    private fun RBuilder.renderSteps() {
        val stepLocations = branchLocations(
                props.objectLocation,
                props.branchAttributePath,
                props.graphStructure
        )
                ?: return

        if (stepLocations.isEmpty()) {
            styledDiv {
                css {
//                    paddingTop = 2.em
                    marginTop = (-2).em
                    paddingLeft = 1.em
                }

                styledDiv {
                    css {
                        width = 10.em
                        fontSize = 1.5.em
                    }

                    +"Empty, please add steps"
                }

                renderInsertionPoint(0)
            }
        }
        else {
            styledDiv {
                css {
                    paddingLeft = 1.em
                }

                renderNonEmptySteps(stepLocations)
            }
        }
    }


    private fun RBuilder.renderInsertionPoint(index: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert action here"
                }
            }

//            +"Index: $index"

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        if (! state.creating) {
                            opacity = 0
                            cursor = Cursor.default
                        }
                    }

                    onClick = {
                        onCreate(index)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderNonEmptySteps(
//            graphStructure: GraphStructure,
//            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>
    ) {
        renderInsertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
//                val objectPath = stepLocation.objectPath

//                +"Step: $stepLocation"
//                val executionState: ImperativeState? =
//                        props.execution?.frames?.lastOrNull()?.states?.get(objectPath)
//
//                val keyLocation = ObjectLocation(documentPath, objectPath)
//
                renderStep(index, stepLocation)

                if (index < stepLocations.size - 1) {
                    renderDownArrowWithInsertionPoint(index + 1)
                }
            }
        }

        renderInsertionPoint(stepLocations.size)
    }


    private fun RBuilder.renderDownArrowWithInsertionPoint(index: Int) {
        styledDiv {
            css {
                position = Position.relative
                height = 4.em
                width = 9.em
            }

            styledDiv {
                css {
                    marginTop = 0.5.em

                    position = Position.absolute
                    height = 1.em
                    width = 1.em
                    top = 0.em
                    left = 0.em
                }
                renderInsertionPoint(index)
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = 8.5.em

                    marginTop =  0.5.em
                    marginBottom = 0.5.em
                }

                child(ArrowDownwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderStep(
            index: Int,
            stepLocation: ObjectLocation
//            graphStructure: GraphStructure,
//            executionState: ImperativeState?
    ) {
        span {
            key = stepLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {

                    attributeNesting = AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index)))

                    objectLocation = stepLocation
                    graphStructure = props.graphStructure
                    imperativeModel = props.imperativeModel
                }
            }
        }
    }
}