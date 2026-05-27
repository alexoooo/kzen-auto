package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.graph.EdgeController
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.objects.document.script.model.StepValidation
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
    init {
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        contextValue<ScriptStore?>()?.observe(this)
    }


    override fun componentWillUnmount() {
        contextValue<ScriptStore?>()?.unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onScriptState(scriptState: ScriptState) {
        val traceInfo = computeStepTraceInfo(scriptState, props.common.objectLocation)

        val stepValidation = scriptState
            .validationState
            .scriptValidation
            ?.stepValidations
            ?.get(props.common.objectLocation.objectPath)

        setState {
            this.isNextToRun = traceInfo.isNextToRun
            this.stepTrace = traceInfo.trace
            this.stepValidation = stepValidation
        }
    }


    override fun onClientState(clientState: ClientState) {
        val headerInfo = computeStepHeaderInfo(clientState, props.common.objectLocation)
            ?: return

        val graphStructure = clientState.graphStructure()
        val objectMetadata = graphStructure
            .graphMetadata
            .objectMetadata[props.common.objectLocation]!!

        val summary = readSummary(graphStructure, props.common.objectLocation)

        setState {
            this.objectMetadata = objectMetadata

            this.icon = headerInfo.icon
            this.description = headerInfo.description
            this.title = headerInfo.title
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
    private fun onToggleExpanded() {
        val next = !state.expanded
        setState {
            this.expanded = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val objectMetadata = state.objectMetadata
            ?: return

        val trace = state.stepTrace
        val isNextToRun = state.isNextToRun ?: false
        val traceState = trace?.state ?: StepTrace.State.Idle

        div {
            css {
                width = ScriptController.stepWidth
                height = 100.pct
                boxSizing = BoxSizing.borderBox
                borderLeftWidth = 4.px
                borderLeftStyle = LineStyle.solid
                borderLeftColor = statusBorderColor(traceState, trace?.error, isNextToRun)
                backgroundColor = NamedColor.white
                paddingLeft = 1.em
                paddingRight = 0.5.em
                paddingTop = 0.5.em
                paddingBottom = 0.5.em
            }

            StepHeader::class.react {
                indexInParent = props.common.indexInParent
                objectLocation = props.common.objectLocation

                managed = false

                icon = state.icon ?: ""
                description = state.description ?: ""
                title = state.title ?: ""

                summary = state.summary
                typeMetadata = state.stepValidation?.typeMetadata?.toSimple()
                expanded = state.expanded
                onToggleExpanded = ::onToggleExpanded
            }

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
            else if (trace != null) {
                div {
                    css {
                        paddingLeft = 1.em
                        paddingTop = 0.5.em
                    }

                    renderValue(trace.displayValue)
                    if (trace.detail !is BinaryExecutionValue) {
                        renderDetail(trace.detail)
                    }
                }
            }
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

        traceSection("Result") {
            div {
                css {
                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                    padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                }
                formatExecutionValueText(value)
            }
        }
    }


    private fun ChildrenBuilder.renderDetail(detail: ExecutionValue) {
        if (detail is NullExecutionValue) {
            return
        }

        traceSection("Detail") {
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

                is ScalarExecutionValue, is ListExecutionValue -> {
                    formatExecutionValueText(detail)
                }

                else -> {
                    // NB: diverges from formatExecutionValueText's bare "$value" — Detail prefixes a label.
                    +"Detail: $detail"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderError(message: String?) {
        if (message == null) {
            return
        }

        traceSection("Error") {
            +"Error: $message"
        }
    }


    private fun ChildrenBuilder.traceSection(
        titleAttr: String,
        content: ChildrenBuilder.() -> Unit
    ) {
        div {
            title = titleAttr
            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }
            content()
        }
    }


    private fun ChildrenBuilder.formatExecutionValueText(value: ExecutionValue) {
        when (value) {
            is ScalarExecutionValue -> +"${value.get()}"
            is ListExecutionValue -> +"${value.values.map { it.get().toString() }}"
            else -> +"$value"
        }
    }


    private fun ChildrenBuilder.renderAttribute(
            attributeName: AttributeName
    ) {
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
    }
}
