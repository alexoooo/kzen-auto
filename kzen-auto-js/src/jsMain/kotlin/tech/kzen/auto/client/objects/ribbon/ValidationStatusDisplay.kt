package tech.kzen.auto.client.objects.ribbon

import emotion.css.keyframes
import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ValidationStatusDisplayProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var logicValidationGlobal: LogicValidationGlobal
}


external interface ValidationStatusDisplayState: State {
    var status: ValidationStatusDisplay.Status
}


//---------------------------------------------------------------------------------------------------------------------
// A compact, fixed-footprint validity indicator tucked UNDER the storage-manager icon (top-right of the header),
// showing the focused Logic document's validation state without ever shifting the surrounding layout (the box is
// always the same size — an empty state simply renders nothing inside it). While the document is (re)validating it
// pulses a small square; when validation settles it resolves to a green check (valid) or a red ✕ (invalid).
//
// Reads the same flavour-agnostic LogicValidationGlobal the run cluster gates on, so it stays in lock-step with
// Run's enabled/disabled state — but purely informational (it never gates anything itself).
class ValidationStatusDisplay(
    props: ValidationStatusDisplayProps
):
    RPureComponent<ValidationStatusDisplayProps, ValidationStatusDisplayState>(props),
    ClientStateGlobal.DocumentScopedObserver,
    LogicValidationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    enum class Status {
        None,       // not a logic document, or no validation known yet — the box is empty (space still reserved)
        Busy,       // (re)validating — a pulsing square
        Valid,      // validated, no error — a green check
        Invalid     // validated, has an error — a red ✕
    }


    companion object {
        // Fixed box so toggling between states never reflows anything around it (the whole point of moving the
        // indicator here off the run-control row).
        private const val boxSize = 18

        private val busyColor = Color("#1565c0")
        private val validColor = Color("#2e7d32")
        private val invalidColor = Color("#c62828")

        // A gentle opacity breathe — reads as "working" without the motion of a spinner.
        private val busyPulse = keyframes {
            from {
                opacity = number(0.9)
            }
            to {
                opacity = number(0.25)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Held outside React state (fresh instances each publish would defeat the shallow shouldComponentUpdate), the
    // inputs the status is derived from — the focused document and whether it is a Logic document.
    private var documentPath: DocumentPath? = null
    private var isLogic: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun ValidationStatusDisplayState.init(props: ValidationStatusDisplayProps) {
        status = Status.None
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        props.logicValidationGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        props.logicValidationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
        val graphNotation = clientState.graphStructure().graphNotation

        this.documentPath = documentPath
        this.isLogic = documentPath != null &&
                graphNotation.documents[documentPath] != null &&
                AutoConventions.isLogic(graphNotation, documentPath)

        updateStatus()
    }


    override fun onLogicValidation(documentPath: DocumentPath) {
        if (documentPath != this.documentPath) {
            return
        }
        updateStatus()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Compared before setState so a no-op publish (the common case — the summary changed for a subset that doesn't
    // move the status bucket) never reaches React.
    private fun updateStatus() {
        val next = computeStatus()
        if (state.status != next) {
            setState {
                status = next
            }
        }
    }


    private fun computeStatus(): Status {
        val documentPath = documentPath
        if (documentPath == null || !isLogic) {
            return Status.None
        }

        val summary = props.logicValidationGlobal.summaryFor(documentPath)
        return when {
            summary.busy -> Status.Busy
            summary.invalidReason != null -> Status.Invalid
            summary.validated -> Status.Valid
            else -> Status.None
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The box is always emitted at a fixed size, so appearing / disappearing / changing glyph never nudges the
        // storage icon above it or anything beside it.
        div {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                width = boxSize.px
                height = boxSize.px
            }

            when (state.status) {
                Status.None ->
                    Unit

                Status.Busy ->
                    renderBusy()

                Status.Valid ->
                    renderGlyph("material-symbols:check", validColor, "Valid")

                Status.Invalid ->
                    renderGlyph("material-symbols:close", invalidColor, "Invalid")
            }
        }
    }


    private fun ChildrenBuilder.renderBusy() {
        div {
            css {
                width = 9.px
                height = 9.px
                borderRadius = 2.px
                backgroundColor = busyColor
                animationName = busyPulse
                animationDuration = 0.9.s
                animationIterationCount = AnimationIterationCount.infinite
                animationDirection = AnimationDirection.alternate
                animationTimingFunction = AnimationTimingFunction.easeInOut
            }
            title = "Revalidating…"
        }
    }


    private fun ChildrenBuilder.renderGlyph(iconName: String, color: Color, tooltip: String) {
        span {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                fontSize = 16.px
                this.color = color
            }
            title = tooltip
            icon(iconName) {}
        }
    }
}
