package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeWrapperLookup
import tech.kzen.auto.client.objects.document.flow.EdgeController
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepDisplayDefaultProps: ScriptStepDisplayBaseProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper

    // Optional extra content rendered at the bottom of the expanded body (inside the click-guarded
    // body region). Null for ordinary steps; RunStepDisplay uses it for the sub-script screenshot
    // strip. NB: plain (non-receiver) function type — receiver function types are prohibited in
    // external declarations; the callee invokes it with the body's ChildrenBuilder.
    var expandedBodyExtra: ((ChildrenBuilder) -> Unit)?
}


external interface ScriptStepDisplayDefaultState: ScriptStepDisplayBaseState {
    var objectMetadata: ObjectMetadata?
    var summaryAttributeNames: List<AttributeName>?

    var expanded: Boolean

    // True when this step's object has attribute-level definition failures — surfaced per-field by the attribute
    // editors — so the (redundant, less specific) step-level validation message is suppressed in the body.
    var hasFieldDefinitionError: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ScriptStepDisplayDefault(
    props: ScriptStepDisplayDefaultProps
):
    ScriptStepDisplayBase<ScriptStepDisplayDefaultProps, ScriptStepDisplayDefaultState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val successColour = Color("#00b467")
        private val errorColour = Color("#b40000")

        // Move-to skipped: a neutral grey bar — the step was short-circuited (value-less) by a forward
        // Set-Next-Statement jump and did not run. Re-runs like Idle if execution comes back around.
        private val skippedColour = Color("#9e9e9e")

        // Validation (can't-run) error accent — deliberately a red-orange, distinct from the darker
        // run-failure red above, so a static "this step won't compile" reads differently from a runtime failure.
        val validationErrorColour = Color("#d84315")

        // Soft-elevation card chrome — lifts a step card off the grey stage; matches the app-wide
        // 3px card radius (Report controllers, VertexController). Single-layer shadow per the
        // StepImageThumbnail idiom. Resting is subtle; hover deepens it as an interactivity cue.
        // Shared with the branch-bearing If/Loop header slab (branchHeaderSlab) so every step card
        // reads with the same elevation.
        val cardCornerRadius = 3.px
        val cardRestingShadow = BoxShadow(0.px, 1.px, 3.px, Color("rgba(0, 0, 0, 0.12)"))
        val cardHoverShadow = BoxShadow(0.px, 2.px, 6.px, Color("rgba(0, 0, 0, 0.18)"))

        fun statusBorderColor(
            traceState: StepTrace.State,
            error: String?,
            nextToRun: Boolean,
            validationError: String? = null
        ): Color {
            // Precedence: an active run status wins (so a running/done/failed step shows its run colour);
            // otherwise a validation error tints the bar red-orange (the step is idle and can't run);
            // otherwise idle white — not a gray line (the gray accent blended into the gray stage; the
            // card's resting shadow provides separation). 4px width is kept even when white, so there's
            // no layout shift when the step transitions to running/done.
            return activeStatusColor(traceState, error, nextToRun)
                ?: if (validationError != null) validationErrorColour else NamedColor.white
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
            else if (traceState == StepTrace.State.Error) {
                // Paused-on-error: red, and ahead of the nextToRun branch so it wins over the gold
                // "next to run" tint (the failed step is also the next to run on resume).
                errorColour
            }
            else if (traceState == StepTrace.State.Skipped) {
                // Short-circuited by a move-to (forward jump over it) — grey, distinct from idle white.
                skippedColour
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
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ScriptStepDisplayDefault::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.objectStableMapper = this@Wrapper.objectStableMapper
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: runs after the base has unobserved both stores, so clearing expansion below doesn't publish
    // onScriptState back into this unmounting component; the still-mounted sibling preview collapses and the
    // deleted step's map entry is pruned.
    override fun onStepUnmount() {
        // Only prune the expansion entry when this step was genuinely DELETED (no longer in the
        // notation). A plain REMOUNT also unmounts us — e.g. selecting a RunStep's sub-script clears a
        // definition error and rebuilds the branch subtree — but the step still exists, so clearing
        // then would wrongly collapse it (editing a step's contents must not collapse it). On document
        // teardown the step also still exists, but the whole ScriptStore (and its steps map) is then
        // discarded, so skipping the clear is harmless. Idempotent — no-ops when already collapsed.
        val stillExists = props.clientStateGlobal.current()
            ?.graphStructure()?.graphNotation?.coalesce
            ?.let { props.common.objectLocation in it }
            ?: false
        if (!stillExists) {
            scriptStore()?.stepStore?.setExpanded(props.common.objectLocation, false)
        }
    }


    override fun onScriptStateExtra(scriptState: ScriptState) {
        val expanded = scriptState.isStepExpanded(props.common.objectLocation)
        if (state.expanded == expanded) {
            return
        }

        setState {
            this.expanded = expanded
        }
    }


    // NB: the base only calls this once the step's metadata is present (computeStepHeaderInfo returned
    // non-null), which is what makes the objectMetadata lookup below safe to force.
    override fun onClientStateExtra(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()
        val objectMetadata = graphStructure
            .graphMetadata
            .objectMetadata[props.common.objectLocation]!!

        val summaryAttributeNames = findSummaryAttributes(objectMetadata)

        // The attribute editors now surface this step's attribute-level definition failures per-field, so the
        // step-level validation message ("Not found") is redundant when any is present (see renderValidation).
        val hasFieldDefinitionError = clientState
            .graphDefinitionAttempt
            .failures.map[props.common.objectLocation]
            ?.attributeErrors?.isNotEmpty() == true

        // NB: value compare (==) — summaryAttributeNames is a fresh List each call (Kotlin List == is
        //     structural). Skip setState on no-op clientState publishes so the RPureComponent conversion
        //     isn't defeated by a fresh-list reference on every broadcast.
        if (state.objectMetadata == objectMetadata &&
            state.summaryAttributeNames == summaryAttributeNames &&
            state.hasFieldDefinitionError == hasFieldDefinitionError
        ) {
            return
        }

        setState {
            this.objectMetadata = objectMetadata
            this.summaryAttributeNames = summaryAttributeNames
            this.hasFieldDefinitionError = hasFieldDefinitionError
        }
    }


    private fun findSummaryAttributes(objectMetadata: ObjectMetadata): List<AttributeName> {
        val result = mutableListOf<AttributeName>()
        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            val hasSummaryView = AttributeWrapperLookup.wrapperName(
                attributeMetadata, AttributeWrapperLookup.summaryAttributePath) != null

            if (hasSummaryView) {
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
        // body and its sibling StepImageThumbnail (no prop path between them) re-render from the
        // resulting onScriptState publish, so there's no local setState here.
        scriptStore()?.stepStore?.setExpanded(props.common.objectLocation, !state.expanded)
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
                borderLeftColor = statusBorderColor(
                    traceState, trace?.error, isNextToRun, state.stepValidation?.errorMessage)
                backgroundColor = NamedColor.white
                paddingLeft = 1.em
                paddingRight = 0.5.em
                paddingTop = 0.5.em
                paddingBottom = 0.5.em

                // Soft elevation: rounded corners + a subtle resting shadow lift the card off the
                // grey stage; the shadow deepens on hover (coincides with the slot's drag-handle
                // reveal) as an interactivity cue. Monochrome palette unchanged.
                borderRadius = cardCornerRadius
                boxShadow = cardRestingShadow
                transition = "box-shadow 120ms ease-out".unsafeCast<Transition>()
                "&:hover" {
                    boxShadow = cardHoverShadow
                }

                // The whole card (its padding/outskirts included) toggles expand/collapse on click.
                cursor = Cursor.pointer
            }

            // Click-to-toggle lives on the card so its padding ring is clickable too. The handled controls —
            // the name text & pencil (edit), the delete/chevron buttons, and (when expanded) the whole body —
            // stop propagation, so only "chrome" clicks (padding, header gaps, summary, collapsed trace) reach
            // here. The drag handle is a slot sibling outside this div, so it never bubbles in.
            onClick = { onToggleExpanded() }

            StepHeader::class.react {
                objectLocation = props.common.objectLocation

                managed = false

                icon = state.icon ?: ""
                description = state.description ?: ""
                title = state.title ?: ""

                summaryAttributeNames = state.summaryAttributeNames
                attributeViewManager = props.attributeViewManager
                typeMetadata = state.stepValidation?.typeMetadata?.toSimple()
                validationError = state.stepValidation?.errorMessage
                skipped = traceState == StepTrace.State.Skipped
                expanded = state.expanded
                onToggleExpanded = ::onToggleExpanded

                mirroredGraphStore = props.mirroredGraphStore
            }

//            +"[x]"
            if (state.expanded) {
                div {
                    css {
                        paddingLeft = 1.em
                        paddingTop = 0.5.em

                        // The expanded body (attribute editors) is NOT a toggle target — override the card's
                        // pointer back to the default and stop clicks below from bubbling up to collapse it.
                        cursor = Cursor.default
                    }

                    onClick = { it.stopPropagation() }

                    renderBody(objectMetadata, trace)
                    renderValidation()
                    props.expandedBodyExtra?.invoke(this)
                }
            }
            else if (trace != null) {
                div {
                    css {
                        paddingLeft = 1.em
                        paddingTop = 0.5.em
                    }

                    // No own handler: this collapsed-only block bubbles to the card's click-to-expand.
                    renderValue(trace.displayValue)
                    if (trace.detail !is BinaryValue) {
                        renderDetail(trace.detail)
                    }
                    renderNote(trace.note)
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
        // Screenshot (BinaryValue) details are shown by the floating StepImageThumbnail to the
        // right, so don't repeat them inline here (matches the collapsed branch's guard).
        if (trace.detail !is BinaryValue) {
            renderDetail(trace.detail)
        }
        renderNote(trace.note)
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


    private fun ChildrenBuilder.renderNote(note: String?) {
        if (note == null) {
            return
        }

        traceSection("Note") {
            div {
                css {
                    color = Color("rgba(0, 0, 0, 0.55)")
                    fontSize = 0.85.em
                }
                +note
            }
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
        // Suppressed when the attribute editors are already showing this step's definition failure(s) per-field:
        // the step-level message (e.g. "Not found") is the same root cause, less specifically.
        if (state.hasFieldDefinitionError == true) {
            return
        }

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
