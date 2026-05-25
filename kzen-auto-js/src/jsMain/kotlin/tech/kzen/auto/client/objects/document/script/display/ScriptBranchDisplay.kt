package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
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
    var scriptCommander: ScriptCommander
}


external interface StepListDisplayState: State {
    var stepLocations: List<ObjectLocation>?

    var creating: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptBranchDisplay(
    props: StepListDisplayProps
):
    RPureComponent<StepListDisplayProps, StepListDisplayState>(props),
    ClientStateGlobal.Observer,
    InsertionGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure

        if (props.attributeLocation.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val stepLocations = ScriptController.stepLocations(
            graphStructure, props.attributeLocation)

        setState {
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
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
            ?: return

        val archetypeObjectLocation = ClientContext.insertionGlobal
            .getAndClearSelection()
            ?: return

        val commands = props.scriptCommander.createCommands(
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

                firstOrLastInsertionPoint(0)
            }
        }
        else {
            div {
                nonEmptySteps(stepLocations)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.nonEmptySteps(
        stepLocations: List<ObjectLocation>
    ) {
        firstOrLastInsertionPoint(0)

        div {
            css {
                width = ScriptController.stepWidth
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                renderStep(
                    index,
                    stepLocation,
                    stepLocations.size
                )

                if (index < stepLocations.size - 1) {
                    betweenStepsInsertionPoint(index + 1)
                }
            }
        }

        firstOrLastInsertionPoint(stepLocations.size)
    }


    private fun ChildrenBuilder.betweenStepsInsertionPoint(index: Int) {
        div {
            css {
                position = Position.relative
                height = 1.5.em
                width = 100.pct
                borderTopWidth = 1.px
                borderTopStyle = LineStyle.solid
                borderTopColor = Color("rgba(0, 0, 0, 0.08)")
            }

            div {
                css {
                    position = Position.absolute
                    top = (-12).px
                    left = 50.pct
                    marginLeft = (-12).px
                }

                insertionButton(index)
            }
        }
    }


    private fun ChildrenBuilder.firstOrLastInsertionPoint(index: Int) {
        div {
            css {
                height = 26.px
                marginTop = 2.px
            }

            insertionButton(index)
        }
    }


    private fun ChildrenBuilder.insertionButton(index: Int) {
        if (!state.creating) {
            return
        }

        IconButton {
            title = "Insert step here"

            css {
                width = 24.px
                height = 24.px
                padding = 0.px
                backgroundColor = NamedColor.white

                hover {
                    backgroundColor = NamedColor.white
                }
            }

            onClick = {
                onCreate(index)
            }

            iconByName("AddCircleOutline") {
                style = unsafeJso {
                    fontSize = 1.em
                }
            }
        }
    }


    private fun ChildrenBuilder.renderStep(
        index: Int,
        objectLocation: ObjectLocation,
        stepCount: Int
    ) {
        span {
            key = Key(objectLocation.toReference().asString())

            props.stepDisplayManager.child(this) {
                common = ScriptStepDisplayPropsCommon(
                    objectLocation,
                    index,
                    first = index == 0,
                    last = index == stepCount - 1,
                )
            }
        }
    }
}