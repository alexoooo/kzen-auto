package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
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
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.objects.document.script.model.StepValidation
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepDisplayDefaultProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
}


external interface ScriptStepDisplayDefaultState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?
    var objectMetadata: ObjectMetadata?
    var stepValidation: StepValidation?

    var icon: String?
    var description: String?
    var title: String?
    var summaryAttributeNames: List<AttributeName>?

    var expanded: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// TODO: can this be made an RPureComponent for conceptual simplicity and optimization?
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
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ScriptStepDisplayDefault::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
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
        val scriptStore = contextValue<ScriptStore?>()
        // Unobserve first so clearing expansion below doesn't publish onScriptState back into this
        // unmounting component; the still-mounted sibling preview collapses and the deleted step's
        // map entry is pruned. Idempotent — no-ops when already collapsed.
        scriptStore?.unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
        scriptStore?.stepStore?.setExpanded(props.common.objectLocation, false)
    }


    override fun onScriptState(scriptState: ScriptState) {
        val traceInfo = computeStepTraceInfo(
            scriptState, props.common.objectLocation, ClientContext.objectStableMapper)

        val stepValidation = scriptState
            .validationState
            .scriptValidation
            ?.stepValidations
            ?.get(props.common.objectLocation.objectPath)

        val expanded = scriptState.isStepExpanded(props.common.objectLocation)

        setState {
            this.isNextToRun = traceInfo.isNextToRun
            this.stepTrace = traceInfo.trace
            this.stepValidation = stepValidation
            this.expanded = expanded
        }
    }


    override fun onClientState(clientState: ClientState) {
        val headerInfo = computeStepHeaderInfo(clientState, props.common.objectLocation)
            ?: return

        val graphStructure = clientState.graphStructure()
        val objectMetadata = graphStructure
            .graphMetadata
            .objectMetadata[props.common.objectLocation]!!

        // TODO: looks like it's recomputed each time, can this be optimized?
        val summaryAttributeNames = findSummaryAttributes(objectMetadata)

        setState {
            this.objectMetadata = objectMetadata

            this.icon = headerInfo.icon
            this.description = headerInfo.description
            this.title = headerInfo.title
            this.summaryAttributeNames = summaryAttributeNames
        }
    }


    private fun findSummaryAttributes(objectMetadata: ObjectMetadata): List<AttributeName> {
        val result = mutableListOf<AttributeName>()
        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            val summaryView = attributeMetadata
                .attributeMetadataNotation
                .get(AttributeViewManager.summaryAttributePath.toNesting())
                ?.asString()
                ?: continue

            if (summaryView.isNotEmpty()) {
                result.add(attributeName)
            }
        }
        return result
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptStepDisplayDefaultState.init(props: ScriptStepDisplayDefaultProps) {
        expanded = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleExpanded() {
        // Single source of truth: write expansion to ScriptState via the sub-store. Both this step
        // body and its sibling StepScreenshotPreview (no prop path between them) re-render from the
        // resulting onScriptState publish, so there's no local setState here.
        contextValue<ScriptStore?>()?.stepStore?.setExpanded(props.common.objectLocation, !state.expanded)
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

                summaryAttributeNames = state.summaryAttributeNames
                attributeViewManager = props.attributeViewManager
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
            if (AutoConventions.isManaged(e.key)) {
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
        // Screenshot (BinaryExecutionValue) details are shown by the floating StepScreenshotPreview
        // to the right, so don't repeat them inline here (matches the collapsed branch's guard).
        if (trace.detail !is BinaryExecutionValue) {
            renderDetail(trace.detail)
        }
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
