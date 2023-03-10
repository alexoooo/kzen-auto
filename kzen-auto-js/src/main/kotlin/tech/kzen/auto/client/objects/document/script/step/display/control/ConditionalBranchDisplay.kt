package tech.kzen.auto.client.objects.document.script.step.display.control

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayPropsCommon
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.material.SubdirectoryArrowLeftIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.platform.collect.persistentListOf



//---------------------------------------------------------------------------------------------------------------------
external interface ConditionalBranchDisplayProps: Props {
    var branchAttributePath: AttributePath

    var stepController: StepController.Wrapper
    var scriptCommander: ScriptCommander

    var clientState: SessionState
    var objectLocation: ObjectLocation
    var imperativeModel: ImperativeModel
}


external interface ConditionalBranchDisplayState: State {
    var creating: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ConditionalBranchDisplay(
        props: ConditionalBranchDisplayProps
):
        RPureComponent<
                ConditionalBranchDisplayProps,
                ConditionalBranchDisplayState
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

            return branchNotations
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ConditionalBranchDisplayState.init(props: ConditionalBranchDisplayProps) {
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
    override fun ChildrenBuilder.render() {
        div {
            css {
                width = 100.pct
                marginLeft = 2.em
            }

            renderSteps()
        }

        SubdirectoryArrowLeftIcon::class.react {
            style = jso {
                fontSize = 3.em
                marginBottom = 15.px
                marginTop = (-60).px
            }
        }
    }


    private fun ChildrenBuilder.renderSteps() {
        val stepLocations = branchLocations(
                props.objectLocation,
                props.branchAttributePath,
                props.clientState.graphStructure()
        ) ?: return

        if (stepLocations.isEmpty()) {
            div {
                css {
                    marginTop = 2.em
                    paddingLeft = 1.em
                    width = 100.pct
                }

                div {
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
            div {
                css {
                    paddingLeft = 1.em
                    width = 100.pct
                }

                renderNonEmptySteps(stepLocations)
            }
        }
    }


    private fun ChildrenBuilder.renderInsertionPoint(
            index: Int
    ) {
        span {
            if (state.creating) {
                title = "Insert action here"
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


    private fun ChildrenBuilder.renderNonEmptySteps(
            stepLocations: List<ObjectLocation>
    ) {
        renderInsertionPoint(0)

        div {
            css {
                minWidth = StepController.width
                width = 100.pct
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


    private fun ChildrenBuilder.renderDownArrowWithInsertionPoint(index: Int) {
        div {
            css {
                position = Position.relative
                height = 4.em
                width = 9.em
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
                renderInsertionPoint(index)
            }

            div {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = 8.5.em

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


    private fun ChildrenBuilder.renderStep(
            index: Int,
            stepLocation: ObjectLocation,
            stepCount: Int
    ) {
        span {
            key = stepLocation.toReference().asString()

            props.stepController.child(this) {
                common = StepDisplayPropsCommon(
                    props.clientState,
                    stepLocation,
                    AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
                    props.imperativeModel,

                    first = index == 0,
                    last = index == stepCount - 1
                )
            }
        }
    }
}