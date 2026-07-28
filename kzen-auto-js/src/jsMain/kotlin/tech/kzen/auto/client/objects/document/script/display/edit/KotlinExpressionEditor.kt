package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import kotlinx.coroutines.delay
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.objects.document.common.valid.ExpressionValidationIndicator
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.model.ScriptStepReferenceStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.None
import web.cssom.Position
import web.cssom.number
import web.cssom.px
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface KotlinExpressionEditorProps: AttributeEditorProps {
    // The per-box "validating…" overlay reflects this document's validation-busy state (see
    // ExpressionValidationIndicator). Only this editor and FormulaMapEditor need it, so it lives on the
    // dedicated props subtype rather than the shared AttributeEditorProps.
    var logicValidationGlobal: LogicValidationGlobal
}


external interface KotlinExpressionEditorState: State {
    // Live edit buffer; null until the first server value hydrates it.
    var value: String?
    // Last value seen from the server, for change detection (so hydration never echoes a write, and
    // submit no-ops when unchanged).
    var serverValue: String?

    // In-scope steps/bindings this expression may reference (the popover options + the canvas highlight set).
    var stepReferences: List<ObjectLocation>?

    // True while THIS editor owns the shared pick session (button toggled on / popover open / cards highlighted).
    var picking: Boolean

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
// Editor for a free-text Kotlin expression attribute that can reference prior in-scope steps by name —
// FormulaStep.code, ResultStep.code, DoWhileStep and IfBranch conditions, and ForEachStep.items (selected
// via `editor: KotlinExpressionEditor`, the only mechanism that picks it). A multiline
// text field (mirrors FormulaItemController's debounced buffer) plus a dual-function insert button: it opens
// a filterable popover of the in-scope steps AND highlights those step cards in the canvas for click-to-insert
// (via the shared ScriptStepReferenceStore). Either path inserts the step's escaped Kotlin variable name at
// the caret; referencing the name is what creates the data dependency (derived lexically server-side).
@Suppress("unused")
class KotlinExpressionEditor(
    props: KotlinExpressionEditorProps
):
    RPureComponent<KotlinExpressionEditorProps, KotlinExpressionEditorState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer,
    ScriptStepReferenceStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val logicValidationGlobal: LogicValidationGlobal
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            KotlinExpressionEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                logicValidationGlobal = this@Wrapper.logicValidationGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The multiline textarea, so insertion can splice at the caret (and restore focus + caret afterwards).
    private val inputRef = createRef<HTMLTextAreaElement>()

    // NB: `this.props` - see the shadowing note in TextAttributeEditor.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = {
            state.value
                ?.takeIf { it != state.serverValue }
                ?.let { ScalarAttributeNotation(it) }
        },
        onError = { message -> setState { errorMessage = message } },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun KotlinExpressionEditorState.init(props: KotlinExpressionEditorProps) {
        value = null
        serverValue = null
        stepReferences = null
        picking = false
        errorMessage = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        scriptStore()?.observe(this)
        referenceStore()?.observe(this)
    }


    override fun componentWillUnmount() {
        // Unobserve the reference store before ending the session, so the resulting clear/publish doesn't
        // call back into this unmounting component.
        val referenceStore = referenceStore()
        referenceStore?.unobserve(this)
        referenceStore?.end(editorLocation())

        scriptStore()?.unobserve(this)
        props.clientStateGlobal.unobserve(this)

        // Flush (not cancel) so a pending debounced edit is committed rather than lost.
        committer.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptStore(): ScriptStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)


    private fun referenceStore(): ScriptStepReferenceStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStepReferenceStoreKey)


    // Attribute-scoped pick-session identity — see ScriptStepReferenceStore.Session.editorLocation for why.
    // NB: a function, not a cached val — these editors outlive a rename of their own host (the manager
    // re-renders them with a new objectLocation), and a property initializer would pin the FIRST render's
    // props, the shadowing hazard documented on SelectReferenceEditorBase's committer.
    private fun editorLocation(): AttributeLocation =
        AttributeLocation(props.objectLocation, AttributePath.ofName(props.attributeName))


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing step deleted/renamed and this objectLocation is stale.
            return
        }

        val attributeNotation = graphNotation.mergeAttribute(
            props.objectLocation, AttributePath.ofName(props.attributeName))
        val newServerValue = (attributeNotation as? ScalarAttributeNotation)?.value ?: ""

        if (newServerValue == state.serverValue) {
            return
        }

        // Adopt the new server value into the buffer only when there's no unsaved local edit (the buffer
        // matched the previous server value, or is uninitialized) — so an external change (e.g. a referenced
        // step rename rewriting this expression) shows up without clobbering in-progress typing. Never submit
        // from here: only user edits / inserts write, so hydration can't echo a no-op command.
        val previousServerValue = state.serverValue
        val adoptIntoBuffer = state.value == null || state.value == previousServerValue

        setState {
            serverValue = newServerValue
            if (adoptIntoBuffer) {
                value = newServerValue
            }
        }
    }


    override fun onScriptState(scriptState: ScriptState) {
        val scriptTree = scriptState.scriptTree
        val targetPath = props.objectLocation.objectPath

        // Read at use time off the synchronous current state, so there is no observer-ordering hazard. Null only
        // before any ClientState exists (unreachable once a ScriptState has been published, but guarded): the
        // editor then falls back to the default scope and self-corrects on the next publish.
        val graphNotation = props.clientStateGlobal.current()?.graphStructure()?.graphNotation

        val candidatePaths =
            if (graphNotation != null &&
                    ScriptConventions.isBodyScopedExpression(
                        graphNotation, props.objectLocation, props.attributeName)
            ) {
                // A `scope: body` expression (DoWhileStep.condition) references the declaring step's own body
                // steps plus in-scope bindings — NOT predecessors. Mirrors DoWhileStep.conditionScopeTypes.
                bodyStepPaths(graphNotation, scriptTree, targetPath) +
                        scriptTree.inScopeBindingPaths(targetPath)
            }
            else {
                // FormulaStep.code references prior steps plus in-scope bindings (parameters / loop items).
                // Mirrors server FormulaStep.processorTypes (same ScriptTree.inScopeReferencePaths).
                scriptTree.inScopeReferencePaths(targetPath)
            }

        val references = candidatePaths.map { props.objectLocation.documentPath.toObjectLocation(it) }

        if (state.stepReferences != references) {
            setState {
                stepReferences = references
            }
        }
    }


    override fun onStepReferenceChanged() {
        val picking = referenceStore()?.session?.editorLocation == editorLocation()
        if (state.picking == picking) {
            return
        }
        setState {
            this.picking = picking
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The direct body steps of the node at `target`: its children under each DISCOVERED branch attribute
    // (metadata `is: List, of: ScriptStep`), in tree order — so an N-branch loop scopes over all of its bodies.
    // The node is found by scanning the tree for the matching objectPath.
    private fun bodyStepPaths(
        graphNotation: GraphNotation,
        scriptTree: ScriptTree,
        target: ObjectPath
    ): List<ObjectPath> {
        val node = findNode(scriptTree, target)
            ?: return listOf()

        val branchNames = ScriptConventions.stepBranchAttributeNames(
            graphNotation, props.objectLocation.documentPath.toObjectLocation(target))

        return branchNames.flatMap { branchName ->
            node.children[branchName]?.map { it.objectPath } ?: listOf()
        }
    }


    private fun findNode(tree: ScriptTree, target: ObjectPath): ScriptTree? {
        if (tree.objectPath == target) {
            return tree
        }
        for (childTrees in tree.children.values) {
            for (childTree in childTrees) {
                val found = findNode(childTree, target)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        committer.schedule()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onBeginPicking() {
        val references = state.stepReferences ?: listOf()
        referenceStore()?.begin(editorLocation(), references.toSet()) { stepLocation ->
            insertReference(stepLocation)
        }
    }


    private fun onEndPicking() {
        referenceStore()?.end(editorLocation())
    }


    private fun onSelectReference(stepLocation: ObjectLocation) {
        // Route the popover selection through the shared store, the same path a canvas card click takes, so
        // the session ends consistently.
        referenceStore()?.selectStep(stepLocation)
    }


    private fun insertReference(stepLocation: ObjectLocation) {
        val current = state.value ?: ""
        val escaped = ExpressionUtils.escapeKotlinVariableName(stepLocation.objectPath.name.value)

        val rawCaret = inputRef.current?.selectionStart ?: current.length
        val caret = rawCaret.coerceIn(0, current.length)

        val newValue = current.substring(0, caret) + escaped + current.substring(caret)
        val newCaret = caret + escaped.length

        setState {
            value = newValue
        }

        // Insertion is a discrete commit — write immediately rather than waiting out the keystroke debounce. The
        // value is passed explicitly: the setState above may not be readable yet, and the buffer-vs-server no-op
        // guard must not suppress this write.
        committer.cancel()
        async {
            committer.commitNow(ScalarAttributeNotation(newValue))
        }

        // Restore focus + caret after React commits the new value (the textarea lost focus to the popover or
        // a canvas card click), so the user can keep typing right after the inserted reference.
        async {
            delay(1)
            val element = inputRef.current
            if (element != null) {
                element.focus()
                element.setSelectionRange(newCaret, newCaret)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val value = state.value
            ?: return
        val references = state.stepReferences ?: listOf()

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.flexStart
            }

            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                    position = Position.relative
                }

                renderTextField(value)

                // "Validating…" pulse overlaid in the field's top-right corner; pointer-events off so it
                // never blocks clicking into the text area.
                div {
                    css {
                        position = Position.absolute
                        top = 4.px
                        right = 4.px
                        pointerEvents = None.none
                    }

                    ExpressionValidationIndicator::class.react {
                        documentPath = props.objectLocation.documentPath
                        logicValidationGlobal = props.logicValidationGlobal
                    }
                }
            }

            StepReferenceController::class.react {
                stepReferences = references
                editDisabled = false
                adding = state.picking
                onAdd = ::onBeginPicking
                onCancel = ::onEndPicking
                onAdded = ::onSelectReference
                addLabel = "Insert step reference"
                addIcon = "material-symbols:functions"
            }
        }
    }


    private fun ChildrenBuilder.renderTextField(value: String) {
        TextField {
            fullWidth = true
            multiline = true
            size = Size.small

            label = ReactNode(formattedLabel())
            this.value = value
            this.inputRef = inputRef

            onChange = {
                val newValue = (it.target as HTMLTextAreaElement).value
                onValueChange(newValue)
            }

            // Commit any pending debounced edit the instant focus leaves the field, so a subsequent separate
            // command (e.g. renaming the step) is applied after this write rather than racing a stale one.
            onBlur = { committer.flush() }

            error = state.errorMessage != null
        }
    }
}
