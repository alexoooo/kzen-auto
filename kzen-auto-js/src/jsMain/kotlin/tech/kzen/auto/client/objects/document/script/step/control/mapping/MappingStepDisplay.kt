package tech.kzen.auto.client.objects.document.script.step.control.mapping

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.model.ScriptGlobal
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.objects.document.script.step.header.StepNameEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.material.ArrowForwardIcon
import tech.kzen.auto.client.wrap.material.SubdirectoryArrowLeftIcon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface MappingStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
}


external interface MappingStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused", "ConstPropertyName")
class MappingStepDisplay(
    props: MappingStepDisplayProps
):
    RComponent<MappingStepDisplayProps, MappingStepDisplayState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val itemsAttributeName = AttributeName("items")

//        val stepsAttributeName = AttributeName("steps")
//        val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

//        private const val tableBorders = true
        private const val tableBorders = false

        private val stepWidth = ScriptController.stepWidth.minus(2.em)
        private val overlapTop = 4.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            MappingStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        ScriptGlobal.get().observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
        ScriptGlobal.get().unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        val objectMetadata = graphStructure
            .graphMetadata
            .objectMetadata[props.common.objectLocation]

        @Suppress("FoldInitializerAndIfToElvis", "RedundantSuppression")
        if (objectMetadata == null) {
            // NB: this step has been deleted, but parent component hasn't re-rendered yet
            return
        }

        val icon = StepHeader.icon(graphStructure, props.common.objectLocation)
        val description = StepHeader.description(graphStructure, props.common.objectLocation)
        val title = StepNameEditor.title(graphStructure, props.common.objectLocation)

        setState {
            this.icon = icon
            this.description = description
            this.title = title
        }
    }


    override fun onScriptState(scriptState: ScriptState, changes: Set<ScriptStore.ChangeType>) {
        val traceValues: Map<LogicTracePath, ExecutionValue>? = scriptState
            .progress
            .logicTraceSnapshot
            ?.values

        val trace = traceValues
            ?.get(LogicTracePath.ofObjectLocation(props.common.objectLocation))
            ?.let { StepTrace.ofExecutionValue(it) }

        val nextToRun = traceValues
            ?.get(ScriptConventions.nextStepTracePath)
            ?.get()
            ?.let {
                ObjectLocation.parse(it as String)
            }

        val isNextToRun = nextToRun == props.common.objectLocation

        setState {
            this.isNextToRun = isNextToRun
            stepTrace = trace
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver() {
        hoverSignal.triggerMouseOver()
    }


    private fun onMouseOut() {
        hoverSignal.triggerMouseOut()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        table {
            css {
                // https://stackoverflow.com/a/24594811/1941359
                height = 100.pct

                if (tableBorders) {
                    borderWidth = 1.px
                    borderStyle = LineStyle.solid
                }

                borderCollapse = BorderCollapse.collapse
            }

            tbody {
                css {
                    if (tableBorders) {
                        borderWidth = 1.px
                        borderStyle = LineStyle.solid
                    }
                }

                tr {
                    td {
                        css {
                            padding = Padding(0.px, 0.px, 0.px, 0.px)
                        }

                        onMouseOver = { onMouseOver() }
                        onMouseOut = { onMouseOut() }

                        renderHeader()
                    }

                    td {}
                }

                tr {
                    td {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding = Padding(0.px, 0.px, 0.px, 0.px)
                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = LineStyle.solid
                            }
                        }

                        renderItems()
                    }
                    td {
                        css {
                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = LineStyle.solid
                            }
                        }

                        renderSteps()
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader() {
        val trace = state.stepTrace
        val isNextToRun = state.isNextToRun ?: false
        val traceState = trace?.state ?: StepTrace.State.Idle

        div {
            css {
                width = stepWidth
                padding = Padding(16.px, 16.px, 0.px, 16.px)
                borderTopLeftRadius = 3.px
                borderTopRightRadius = 3.px
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                backgroundColor = ScriptStepDisplayDefault.backgroundColor(traceState, trace?.error, isNextToRun)
            }

            StepHeader::class.react {
                hoverSignal = this@MappingStepDisplay.hoverSignal

                indexInParent = props.common.indexInParent
                objectLocation = props.common.objectLocation

//                managed = props.common.managed
                managed = false
                first = props.common.first
                last = props.common.last

                icon = state.icon ?: ""
                description = state.description ?: ""
                title = state.title ?: ""
            }
        }
    }


    private fun ChildrenBuilder.renderItems() {
        div {
            css {
                width = stepWidth
                padding = Padding(0.em, 1.em, 0.em, 1.em)
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                height = 100.pct

                backgroundColor = NamedColor.white
//                backgroundColor = when {
//                    imperativeState?.previous is ExecutionSuccess ->
//                        Color("#00b467")
//
//                    inThenBranch ->
//                        EdgeController.goldLight75
//
//                    else ->
//                        NamedColor.white
//                }
            }

//            +"[Condition]"
            props.attributeEditorManager.child(this) {
//                this.clientState = props.common.clientState
                this.objectLocation = props.common.objectLocation
                this.attributeName = itemsAttributeName
            }
        }
    }


    private fun ChildrenBuilder.renderSteps() {
        div {
            css {
                width = 100.pct
                marginBottom = overlapTop
            }

            div {
                css {
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Each"
                br {}
                ArrowForwardIcon::class.react {
                    style = unsafeJso {
                        fontSize = 3.em
                    }
                }
            }

            div {
                css {
                    width = 100.pct.minus(3.em)
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                    marginLeft = 3.5.em
                }

                ScriptBranchDisplay::class.react {
                    attributeLocation = AttributeLocation(
                        props.common.objectLocation, ScriptConventions.stepsAttributePath)
                    nested = true

                    stepDisplayManager = props.stepDisplayManager
                    scriptCommander = props.scriptCommander
                }
            }

            div {
                SubdirectoryArrowLeftIcon::class.react {
                    style = unsafeJso {
                        fontSize = 3.em
                        marginBottom = 15.px
                        marginTop = (-40).px
                    }
                }
            }
        }
    }
}