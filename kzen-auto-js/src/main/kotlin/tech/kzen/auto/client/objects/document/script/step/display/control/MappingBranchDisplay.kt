package tech.kzen.auto.client.objects.document.script.step.display.control

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.attrs
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayPropsCommon
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.platform.collect.persistentListOf


//---------------------------------------------------------------------------------------------------------------------
external interface MappingBranchDisplayProps: Props {
    var branchAttributePath: AttributePath

    var stepController: StepController.Wrapper
    var scriptCommander: ScriptCommander

    var clientState: SessionState
    var objectLocation: ObjectLocation
    var imperativeModel: ImperativeModel
}


external interface MappingBranchDisplayState: State {
    var creating: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class MappingBranchDisplay(
        props: MappingBranchDisplayProps
):
        RPureComponent<
                MappingBranchDisplayProps,
                MappingBranchDisplayState
                >(props),
        InsertionGlobal.Subscriber
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
                    .firstAttribute(stepLocation, branchAttributePath)
                    as? ListAttributeNotation
                    ?: return null

            val host = ObjectReferenceHost.ofLocation(stepLocation)
            return branchNotations
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it, host) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun MappingBranchDisplayState.init(props: MappingBranchDisplayProps) {
        creating = false
    }


    override fun componentDidMount() {
        async {
            ClientContext.insertionGlobal.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
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
        val archetypeObjectLocation = ClientContext.insertionGlobal
                .getAndClearSelection()
                ?: return

        val commands = props.scriptCommander.createCommands(
                props.objectLocation,
                props.branchAttributePath,
                index,
                archetypeObjectLocation,
                props.clientState.graphStructure())

        async {
            for (command in commands) {
                ClientContext.mirroredGraphStore.apply(command)
            }
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
                props.clientState.graphStructure()
        ) ?: return

        if (stepLocations.isNotEmpty()) {
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
            stepLocations: List<ObjectLocation>
    ) {
        styledDiv {
            css {
                width = StepController.width
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                renderStep(index, stepLocation, stepLocations.size)

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
                width = StepController.width.div(2).minus(1.em)
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
                    left = StepController.width.div(2).minus(1.em)

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
            stepLocation: ObjectLocation,
            stepCount: Int
    ) {
        span {
            key = stepLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {
                    common = StepDisplayPropsCommon(
                            props.clientState,
                            stepLocation,
                            AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
                            props.imperativeModel,

                            managed = index == 0,
                            first = index == 1,
                            last = index == stepCount - 1
                    )
                }
            }
        }
    }
}