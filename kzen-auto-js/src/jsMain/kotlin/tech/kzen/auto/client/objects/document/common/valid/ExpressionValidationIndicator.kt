package tech.kzen.auto.client.objects.document.common.valid

import emotion.css.keyframes
import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.document.DocumentPath
import web.cssom.AnimationDirection
import web.cssom.AnimationIterationCount
import web.cssom.AnimationTimingFunction
import web.cssom.Color
import web.cssom.number
import web.cssom.px
import web.cssom.s


//---------------------------------------------------------------------------------------------------------------------
external interface ExpressionValidationIndicatorProps: Props {
    var documentPath: DocumentPath
    var logicValidationGlobal: LogicValidationGlobal
}


external interface ExpressionValidationIndicatorState: State {
    var busy: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// A small "validating…" pulse for a single Kotlin-expression editor box, reflecting the editor's DOCUMENT
// validation-busy state — LogicValidationGlobal is keyed per document, not per object (the whole document
// co-validates as a unit: its expressions reference each other by name), so every expression box in the
// document pulses together during a pass. Shared by KotlinExpressionEditor (FormulaStep / ResultStep /
// DoWhileStep) and the Job FormulaMapEditor; the host overlays it in the box's corner. Renders nothing when
// idle. Same blue opacity-breathe as the header's ValidationStatusDisplay, so the per-box and global
// indicators read as one.
class ExpressionValidationIndicator(
    props: ExpressionValidationIndicatorProps
):
    RPureComponent<ExpressionValidationIndicatorProps, ExpressionValidationIndicatorState>(props),
    LogicValidationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val busyColor = Color("#1565c0")

        // A gentle opacity breathe — reads as "working" without the motion of a spinner (matches
        // ValidationStatusDisplay.renderBusy).
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
    override fun ExpressionValidationIndicatorState.init(props: ExpressionValidationIndicatorProps) {
        busy = props.logicValidationGlobal.summaryFor(props.documentPath).busy
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.logicValidationGlobal.observe(this)
        // Recompute in case busy toggled between init and mount (summaryFor is read on demand, no replay).
        updateBusy()
    }


    override fun componentWillUnmount() {
        props.logicValidationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onLogicValidation(documentPath: DocumentPath) {
        if (documentPath != props.documentPath) {
            return
        }
        updateBusy()
    }


    // Compared before setState so a publish that doesn't move THIS document's busy bucket never reaches React.
    private fun updateBusy() {
        val next = props.logicValidationGlobal.summaryFor(props.documentPath).busy
        if (state.busy != next) {
            setState {
                busy = next
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (!state.busy) {
            return
        }

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
            title = "Validating…"
        }
    }
}
