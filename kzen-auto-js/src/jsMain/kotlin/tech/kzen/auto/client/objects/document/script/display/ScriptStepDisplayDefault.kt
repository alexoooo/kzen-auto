package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.graph.EdgeController
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.model.ScriptGlobal
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.objects.document.script.step.header.StepNameEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.objects.document.script.model.StepValidation
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.IoUtils
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepDisplayDefaultProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface ScriptStepDisplayDefaultState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?
    var objectMetadata: ObjectMetadata?
    var stepValidation: StepValidation?

    var icon: String?
    var description: String?
    var title: String?
    var summary: String?

    var expanded: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ScriptStepDisplayDefault(
    props: ScriptStepDisplayDefaultProps
):
    RComponent<ScriptStepDisplayDefaultProps, ScriptStepDisplayDefaultState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val successColour = Color("#00b467")
        private val errorColour = Color("#b40000")
        private val idleBorderColour = Color("rgba(0, 0, 0, 0.12)")

        fun statusBorderColor(
            traceState: StepTrace.State,
            error: String?,
            nextToRun: Boolean
        ): Color {
            return activeStatusColor(traceState, error, nextToRun) ?: idleBorderColour
        }

        fun backgroundColor(
            traceState: StepTrace.State,
            error: String?,
            nextToRun: Boolean
        ): Color {
            return activeStatusColor(traceState, error, nextToRun) ?: NamedColor.white
        }

        private fun activeStatusColor(
            traceState: StepTrace.State,
            error: String?,
            nextToRun: Boolean
        ): Color? {
            return if (traceState == StepTrace.State.Running) {
                NamedColor.gold
            }
            else if (traceState == StepTrace.State.Active) {
                EdgeController.goldLight90
            }
            else if (traceState == StepTrace.State.Done) {
                if (error != null) {
                    errorColour
                }
                else {
                    successColour
                }
            }
            else if (nextToRun) {
                EdgeController.goldLight50
            }
            else {
                null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ScriptStepDisplayDefault::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
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


    override fun onScriptState(scriptState: ScriptState, changes: Set<ScriptStore.ChangeType>) {
        val traceValues: Map<LogicTracePath, ExecutionValue>? = scriptState
            .progress
            .logicTraceSnapshot
            ?.values

        val stepTrace = traceValues
            ?.get(LogicTracePath.ofObjectLocation(props.common.objectLocation))
            ?.let { StepTrace.ofExecutionValue(it) }

        val nextToRun = traceValues
            ?.get(ScriptConventions.nextStepTracePath)
            ?.get()
            ?.let {
                ObjectLocation.parse(it as String)
            }

        val isNextToRun = nextToRun == props.common.objectLocation

        val stepValidation = scriptState
            .validationState
            .scriptValidation
            ?.stepValidations
            ?.get(props.common.objectLocation.objectPath)

        setState {
            this.isNextToRun = isNextToRun
            this.stepTrace = stepTrace
            this.stepValidation = stepValidation
        }
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
        val summary = readSummary(graphStructure, props.common.objectLocation)

        setState {
            this.objectMetadata = objectMetadata

            this.icon = icon
            this.description = description
            this.title = title
            this.summary = summary
        }
    }


    private fun readSummary(
        graphStructure: GraphStructure,
        objectLocation: ObjectLocation
    ): String? {
        val summaryAttributeName = graphStructure.graphNotation
            .firstAttribute(objectLocation, ScriptConventions.summaryAttributePath)
            ?.asString()
            ?: return null

        return graphStructure.graphNotation
            .firstAttribute(objectLocation, AttributePath.ofName(AttributeName(summaryAttributeName)))
            ?.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptStepDisplayDefaultState.init(props: ScriptStepDisplayDefaultProps) {
        expanded = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver() {
        hoverSignal.triggerMouseOver()
    }


    private fun onMouseOut() {
        hoverSignal.triggerMouseOut()
    }


    private fun onToggleExpanded() {
        val next = !state.expanded
        setState {
            this.expanded = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        span {
            css {
                width = ScriptController.stepWidth
            }

            onMouseOver = { onMouseOver() }
            onMouseOut = { onMouseOut() }

            renderCard()
        }
    }


    private fun ChildrenBuilder.renderCard() {
        val objectMetadata = state.objectMetadata
            ?: return

        val trace = state.stepTrace
        val isNextToRun = state.isNextToRun ?: false
        val traceState = trace?.state ?: StepTrace.State.Idle

        div {
            css {
                borderLeftWidth = 4.px
                borderLeftStyle = LineStyle.solid
                borderLeftColor = statusBorderColor(traceState, trace?.error, isNextToRun)
                backgroundColor = NamedColor.white
                paddingLeft = 1.5.em
                paddingRight = 0.5.em
                paddingTop = 0.75.em
                paddingBottom = 0.5.em
            }

            renderRow()

            if (state.expanded) {
                div {
                    css {
                        paddingLeft = 1.em
                        paddingTop = 0.5.em
                    }

                    renderBody(objectMetadata, trace)
                    renderValidation()
                }
            }
        }
    }


    private fun ChildrenBuilder.renderRow() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                width = 100.pct
                height = StepHeader.headerHeight
            }

            div {
                css {
                    flexGrow = number(1.0)
                    position = Position.relative
                    height = 100.pct
                    minWidth = 0.px
                }

                StepHeader::class.react {
                    hoverSignal = this@ScriptStepDisplayDefault.hoverSignal

                    indexInParent = props.common.indexInParent
                    objectLocation = props.common.objectLocation

                    first = props.common.first
                    last = props.common.last

                    icon = state.icon ?: ""
                    description = state.description ?: ""
                    title = state.title ?: ""
                }

                renderSummaryOverlay()
            }

            IconButton {
                title = if (state.expanded) "Collapse" else "Expand"

                sx {
                    width = 28.px
                    height = 28.px
                    padding = 0.px
                }

                onClick = { onToggleExpanded() }

                iconByName(if (state.expanded) "KeyboardArrowUp" else "KeyboardArrowDown") {}
            }
        }
    }


    private fun ChildrenBuilder.renderSummaryOverlay() {
        val summary = state.summary
        if (summary.isNullOrEmpty()) {
            return
        }

        div {
            css {
                position = Position.absolute
                top = 0.em
                bottom = 0.em
                left = 11.em
                right = 2.5.em
                display = Display.flex
                alignItems = AlignItems.center
                color = Color("rgba(0, 0, 0, 0.55)")
                fontSize = 0.85.em
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                pointerEvents = None.none
            }

            +summary
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBody(
        objectMetadata: ObjectMetadata,
        trace: StepTrace?
    ) {
        for (e in objectMetadata.attributes.map) {
            if (ScriptConventions.isManaged(e.key)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                renderAttribute(e.key)
            }
        }

        if (trace == null) {
            return
        }

        renderValue(trace.displayValue)
        renderDetail(trace.detail)
        renderError(trace.error)
    }


    private fun ChildrenBuilder.renderValue(value: ExecutionValue) {
        if (value is NullExecutionValue) {
            return
        }

        div {
            title = "Result"

            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            div {
                css {
                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                    padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                }

                when (value) {
                    is ScalarExecutionValue -> {
                        +"${value.get()}"
                    }

                    is ListExecutionValue -> {
                        val textValues = value.values.map { it.get().toString() }
                        +"$textValues"
                    }

                    else -> {
                        +"$value"
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderDetail(detail: ExecutionValue) {
        if (detail is NullExecutionValue) {
            return
        }

        div {
            title = "Detail"

            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            when (detail) {
                is BinaryExecutionValue -> {
                    val screenshotPngUrl = detail.cache("img") {
                        val base64 = IoUtils.base64Encode(detail.value)
                        "data:png/png;base64,$base64"
                    }

                    img {
                        css {
                            width = 100.pct
                        }
                        src = screenshotPngUrl
                    }
                }

                is ScalarExecutionValue -> {
                    +"${detail.get()}"
                }

                is ListExecutionValue -> {
                    val valueStrings = detail.values.map { it.get().toString() }
                    +"$valueStrings"
                }

                else -> {
                    +"Detail: $detail"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderError(message: String?) {
        if (message == null) {
            return
        }

        div {
            title = "Error"

            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            +"Error: $message"
        }
    }


    private fun ChildrenBuilder.renderAttribute(
            attributeName: AttributeName
    ) {
//        +"[Attribute - $attributeName - ${props.attributeEditorManager}]"

        props.attributeEditorManager.child(this) {
            this.objectLocation = props.common.objectLocation
            this.attributeName = attributeName
        }
    }


    private fun ChildrenBuilder.renderValidation() {
        val stepValidation = state.stepValidation
            ?: return

        val errorMessage = stepValidation.errorMessage
        if (errorMessage != null) {
            div {
                +"Error: $errorMessage"
            }
        }

        val typeMetadata = stepValidation.typeMetadata
        if (typeMetadata != null) {
            div {
                +"Type: ${typeMetadata.toSimple()}"
            }
        }
    }
}