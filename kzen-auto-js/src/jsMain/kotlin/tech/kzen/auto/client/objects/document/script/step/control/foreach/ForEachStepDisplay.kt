package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptBranchDisplay
import tech.kzen.auto.client.objects.document.script.display.dependency.StepDependencyEdges
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.scriptDependencyAnalysis
import tech.kzen.auto.client.objects.document.script.model.stepRowRefRegistry
import tech.kzen.auto.client.objects.document.script.step.control.BranchStepDisplayProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ForEachStepDisplayState: ScriptStepDisplayBaseState {
    var itemLocation: ObjectLocation?
    var itemTypeMetadata: String?

    // Dependency lanes for the single item row. The item's consumers are body steps, so its edges are always
    // cross-branch — this carries the phantom source marker ScriptDependencyOverlay's polyline emerges from.
    var itemEdges: StepDependencyEdges?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ForEachStepDisplay(
    props: BranchStepDisplayProps
):
    ScriptStepDisplayBase<BranchStepDisplayProps, ForEachStepDisplayState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Body content's left offset from the card edge, clearing the accent band and scope line drawn there.
        // Keep at branchRailWidth: the rail is paint-only (absolutely positioned, a few px wide), so this
        // indent is the only thing keeping the item row and the step rows off it.
        private val bodyIndent = branchRailWidth
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ForEachStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander
                clientStateGlobal = this@Wrapper.clientStateGlobal
                objectStableMapper = this@Wrapper.objectStableMapper
                mirroredGraphStore = this@Wrapper.mirroredGraphStore

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The loop item binding (ForEachItemBinding) lives in this ForEach's `item` branch. There is at most one, and
    // its name is its own — the row renders it rather than a hardcoded label.
    private fun itemObjectPath(documentNotation: DocumentNotation): ObjectPath? {
        return documentNotation
            .directNestedObjectPaths(
                props.common.objectLocation.objectPath, ScriptConventions.itemAttributeName)
            .firstOrNull()
    }


    // The item's type is the items-collection element type, resolved server-side by ForEachItemBinding.definition
    // and delivered through validation. Null while the collection is still deferring its own type.
    override fun onScriptStateExtra(scriptState: ScriptState) {
        val itemTypeMetadata = itemObjectPath(scriptState.documentNotation)?.let {
            scriptState
                .validationState
                .scriptValidation
                ?.stepValidations
                ?.get(it)
                ?.typeMetadata
                ?.toSimple()
        }

        if (state.itemTypeMetadata == itemTypeMetadata) {
            return
        }

        setState {
            this.itemTypeMetadata = itemTypeMetadata
        }
    }


    // The item row's location (its registry key and dependency-edge identity) and its gutter lanes. Derived here
    // rather than in onScriptStateExtra because the memoized dependency analysis is keyed on the client state.
    override fun onClientStateExtra(clientState: ClientState) {
        val documentPath = props.common.objectLocation.documentPath

        val itemLocation = clientState
            .graphDefinitionAttempt
            .graphStructure
            .graphNotation
            .documents[documentPath]
            ?.let { itemObjectPath(it) }
            ?.let { ObjectLocation(documentPath, it) }

        val itemEdges = itemLocation?.let {
            StepDependencyEdges.compute(listOf(it), scriptDependencyAnalysis(clientState, documentPath))
        }

        // NB: value compare (==) — compute allocates a fresh StepDependencyEdges each fire, so a reference guard
        //     would never bail and every progress tick would re-render the whole loop body.
        if (state.itemLocation == itemLocation && state.itemEdges == itemEdges) {
            return
        }

        setState {
            this.itemLocation = itemLocation
            this.itemEdges = itemEdges
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The loop's live iteration progress, which ForEachStep traces as this step's own detail (the loop is the
        // current step while its body runs). Parsed per render rather than held in state: the base class already
        // guards state.stepTrace by value, so this only re-runs when the trace actually changed.
        val trace = state.stepTrace
        val progress = ForEachProgress.ofExecutionValueOrNull(trace?.detail)

        // The stage's accent band continues this same bar down the card's left edge, so both read it here.
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            trace?.state ?: StepTrace.State.Idle,
            trace?.error,
            state.isNextToRun ?: false,
            state.stepValidation?.errorMessage)

        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = trace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage,
            partial = progress?.partial ?: false
        ) {
            // Vertical breathing room the slab's own body padding does not give (it is zero top/bottom):
            // items is a MULTILINE Kotlin expression field now, not the compact select it used to be, so its
            // outline would otherwise sit flush against the header above and the stage seam below. Matches
            // the padding DoWhileStepDisplay gives its condition editor.
            div {
                css {
                    padding = Padding(12.px, 0.px, 12.px, 0.px)
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = props.common.objectLocation
                    this.attributeName = ScriptConventions.itemsAttributeName
                }
            }
        }

        // Recessed stage: branchStageAccentRail continues the header slab's status bar down the card's left edge
        // under a thin scope line, grouping the body the way an editor's indent guide groups a block, and fading
        // out at the bottom where the stage ends on the open page.
        div {
            css {
                position = Position.relative
            }

            branchStageAccentRail(accent, fadeBottom = true)

            branchStageSeam()
            branchStageTopShadow {
                renderBody(progress)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Item row and body steps share one indented container so their dependency gutters line up in a single column
    // — which is what lets the overlay's item -> step polyline read as one continuous elbow.
    private fun ChildrenBuilder.renderBody(progress: ForEachProgress?) {
        div {
            css {
                marginLeft = bodyIndent
                minHeight = 4.em
            }

            renderItem(progress)

            ScriptBranchDisplay::class.react {
                attributeLocation = AttributeLocation(
                    props.common.objectLocation, ScriptConventions.stepsAttributePath)
                nested = true
                stepDisplayManager = props.stepDisplayManager
                scriptCommander = props.scriptCommander
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
                objectStableMapper = props.objectStableMapper
            }
        }
    }


    private fun ChildrenBuilder.renderItem(progress: ForEachProgress?) {
        val itemLocation = state.itemLocation
            ?: return

        forEachItemRow(
            itemLocation = itemLocation,
            itemType = state.itemTypeMetadata,
            // Carries the live per-iteration item value AND the loop's counter / value journal — no separate
            // binding trace is needed, since the loop is the current step while its body runs.
            progress = progress,
            registry = stepRowRefRegistry(),
            edges = state.itemEdges ?: StepDependencyEdges.EMPTY)
    }
}
