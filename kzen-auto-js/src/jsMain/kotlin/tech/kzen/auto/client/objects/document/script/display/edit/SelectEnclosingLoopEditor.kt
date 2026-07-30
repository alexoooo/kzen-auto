package tech.kzen.auto.client.objects.document.script.display.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.objects.document.script.model.ScriptNestingAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
// Loop-target dropdown for ControlStep.loop: unlike SelectStepEditor (which lists a step's predecessors), the
// candidate set is the ENCLOSING loops from ScriptNestingAnalysis.enclosingLoops — the exact set ControlStep's
// server-side definition() validates against, so dropdown and validation stay in lock-step. On a fresh (empty)
// loop it pre-fills the innermost enclosing loop, so an inserted-and-expanded ControlStep is valid by default.
// While the dropdown is open an enclosing loop can equally be chosen by clicking its card on the canvas — see
// StepPickingSelectEditorBase.
@Suppress("unused")
class SelectEnclosingLoopEditor(
    props: AttributeEditorProps
):
    StepPickingSelectEditorBase(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectEnclosingLoopEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // enclosingLoops needs both graphNotation (ClientState) and scriptTree (ScriptStore); cache each as it
    // arrives (plain fields, not state — the large graphNotation would defeat the shallow-equal and re-render
    // on every keystroke) and recompute the candidate list when both are present.
    private var latestGraphNotation: GraphNotation? = null
    private var latestScriptTree: ScriptTree? = null

    // Plain-field mirror of the resolved selection + a hydrated flag, so the pre-fill guard reads the freshly
    // resolved emptiness rather than the not-yet-applied `state` (setState is async, so `state.selected` is
    // stale within the callback that scheduled it).
    private var latestSelectedKey: String? = null
    private var latestHydrated = false
    private var defaultApplied = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        latestGraphNotation = graphNotation

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val value =
            (attributeNotation as? ScalarAttributeNotation)
                ?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    val reference = ObjectReference.parse(it)
                    graphNotation.coalesce.locateOptional(reference, objectReferenceHost)
                }

        latestSelectedKey = value?.asString()
        latestHydrated = true

        setSelected(latestSelectedKey)

        recomputeCandidates()
    }


    override fun onScriptState(scriptState: ScriptState) {
        latestScriptTree = scriptState.scriptTree
        recomputeCandidates()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun recomputeCandidates() {
        val graphNotation = latestGraphNotation
            ?: return
        val scriptTree = latestScriptTree
            ?: return

        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }

        val candidates = ScriptNestingAnalysis.enclosingLoops(
            graphNotation,
            props.objectLocation.documentPath,
            scriptTree,
            props.objectLocation.objectPath)

        setOptions(candidates
            .map { location ->
                val option: SelectOption = unsafeJso {
                    value = location.asString()
                    label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray())

        // Pre-fill the innermost enclosing loop when this control step's target is still unset — makes a
        // freshly inserted ControlStep valid without manual selection. Runs once, as a single deliberate write
        // through the base's one commit path. Guarded on the plain-field mirrors, not `state`.
        if (!defaultApplied && latestHydrated && latestSelectedKey == null && candidates.isNotEmpty()) {
            defaultApplied = true
            latestSelectedKey = candidates.first().asString()
            selectAndCommit(latestSelectedKey!!)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        if (event is RenamedObjectRefactorEvent &&
                event.renamedObject.objectLocation.asString() == state.selected
        ) {
            latestSelectedKey = event.renamedObject.newObjectLocation().asString()
            setSelected(latestSelectedKey)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun wireValue(optionKey: String): String {
        return ObjectLocation.parse(optionKey)
            .toReference()
            .crop(retainPath = false)
            .asString()
    }
}
