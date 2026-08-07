package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import kotlinx.coroutines.delay
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.CodeCompletion
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.KotlinCodeArea
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.objects.document.common.valid.ExpressionValidationIndicator
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.model.ScriptStepReferenceStoreKey
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer
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

    // [stepReferences] reduced to the identifier contents an expression would name them by. Derived once per
    // change rather than per render, so the code area's shallow prop compare isn't defeated by a fresh Set.
    var knownIdentifiers: Set<String>?

    // [stepReferences] as completion options, each carrying the type the validator resolved for that step.
    // Derived once per change for the same reason as [knownIdentifiers].
    var completions: List<CodeCompletion>?

    // True while THIS editor owns the shared pick session (button toggled on / popover open / cards highlighted).
    var picking: Boolean

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    // NOT the validation finding below — a write failure is about saving the text, not about what it means.
    var errorMessage: String?

    // This step's server-side validation finding: the message the field prints, and where in the validated
    // expression text it points (null when the finding has no position there). [validationLoaded] mirrors
    // ScriptValidationState.loaded — false while a pass is in flight, when the finding still on screen
    // describes the notation of the PREVIOUS pass.
    var validationError: String?
    var validationErrorOffset: Int?
    var validationLoaded: Boolean?
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
    ObjectScopedComponent<KotlinExpressionEditorProps, KotlinExpressionEditorState>(props),
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
    companion object {
        // A step that yields nothing has no type worth listing beside its name, matching the step card's own
        // type chip (StepHeader).
        private const val voidTypeName = "Unit"
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

    // Bound once per instance rather than per render, so KotlinCodeArea's shallow prop compare can actually
    // short-circuit — a fresh closure each render would make every prop set look changed.
    private val onValueChangeHandler: (String) -> Unit = ::onValueChange
    private val onReplaceRangeHandler: (Int, Int, String) -> Unit = ::replaceRange

    // Commit any pending debounced edit the instant focus leaves the field, so a subsequent separate command
    // (e.g. renaming the step) is applied after this write rather than racing a stale one.
    private val onBlurHandler: () -> Unit = { committer.flush() }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun KotlinExpressionEditorState.init(props: KotlinExpressionEditorProps) {
        value = null
        serverValue = null
        stepReferences = null
        knownIdentifiers = null
        completions = null
        picking = false
        errorMessage = null
        validationError = null
        validationErrorOffset = null
        validationLoaded = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        super.componentDidMount()
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
        super.componentWillUnmount()

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
            val identifiers = knownIdentifiersOf(references)
            setState {
                stepReferences = references
                knownIdentifiers = identifiers
            }
        }

        // The identical lookup every step display performs (ScriptStepDisplayBase.onScriptState) — this editor
        // reads it directly so the finding renders at the field it belongs to. Guarded separately from the
        // references above so an unchanged slice keeps its state identity; React batches the two writes.
        val validationState = scriptState.validationState

        val stepValidation = validationState
            .scriptValidation
            ?.stepValidations
            ?.get(targetPath)

        val validationError = stepValidation?.errorMessage
        val validationErrorOffset = stepValidation?.errorOffset
        val validationLoaded = validationState.loaded

        if (state.validationError != validationError ||
                state.validationErrorOffset != validationErrorOffset ||
                state.validationLoaded != validationLoaded
        ) {
            setState {
                this.validationError = validationError
                this.validationErrorOffset = validationErrorOffset
                this.validationLoaded = validationLoaded
            }
        }

        // Guarded on its own rather than riding on the references guard above: a referenced step's type settles
        // on a later validation pass, with the reference list unchanged.
        val completions = completionsOf(references, validationState.scriptValidation)
        if (state.completions != completions) {
            setState {
                this.completions = completions
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


    // Splices at the caret without consuming any selection, matching what a click on a step card means: put
    // this name here.
    private fun insertReference(stepLocation: ObjectLocation) {
        val current = state.value ?: ""
        val caret = (inputRef.current?.selectionStart ?: current.length).coerceIn(0, current.length)

        replaceRange(
            caret, caret, ExpressionUtils.escapeKotlinVariableName(stepLocation.objectPath.name.value))
    }


    // The one write path for text the user did not type — a step reference, an accepted completion — as opposed
    // to the debounced keystroke path.
    private fun replaceRange(start: Int, endExclusive: Int, text: String) {
        val current = state.value ?: ""
        val from = start.coerceIn(0, current.length)
        val to = endExclusive.coerceIn(from, current.length)

        val newValue = current.substring(0, from) + text + current.substring(to)
        val newCaret = from + text.length

        setState {
            value = newValue
        }

        // A splice is a discrete commit — write immediately rather than waiting out the keystroke debounce. The
        // value is passed explicitly: the setState above may not be readable yet, and the buffer-vs-server no-op
        // guard must not suppress this write.
        committer.cancel()
        async {
            committer.commitNow(ScalarAttributeNotation(newValue))
        }

        // Put focus and the caret back after React commits the new value — the field may have lost focus to the
        // popover or a canvas card click, and a controlled re-render drops the caret to the end regardless — so
        // the user can keep typing right where the spliced text ends.
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
        KotlinCodeArea::class.react {
            this.value = value
            this.textAreaRef = inputRef
            this.knownIdentifiers = state.knownIdentifiers ?: setOf()
            label = formattedLabel()
            disabled = false

            onChange = onValueChangeHandler
            onBlur = onBlurHandler

            // Two independent failures reaching one field: the validation finding is the message the field
            // prints and marks, while a failed notation write only reddens its outline (the write's own
            // message is announced by the global banner).
            errorMessage = state.validationError
            invalid = state.errorMessage != null
            errorRange = errorRange(value)

            completions = state.completions
            onReplaceRange = onReplaceRangeHandler
        }
    }


    // The span to mark, or null when there is no position worth showing — the message can be on screen with
    // no marker under it, which is the point. The offset was computed against the notation the server
    // validated, so it is aimed at this buffer only once the buffer matches that notation AND the pass that
    // produced it has settled; mid-edit or mid-revalidation an offset describes different text, and a marker
    // at the wrong token is worse than none (the ExpressionValidationIndicator pulse covers the transient).
    // An offset outside the buffer is likewise dropped, never clamped.
    private fun errorRange(value: String): IntRange? {
        val offset = state.validationErrorOffset
            ?: return null

        val describesBuffer = state.validationLoaded == true && value == state.serverValue
        if (!describesBuffer || offset < 0 || offset > value.length) {
            return null
        }

        // Tokens cover exactly `0 until value.length`, so value.length — the one-past-the-end position a
        // parse error reports — is the only in-range offset with no containing token, and marks end-of-text.
        val containingToken = KotlinExpressionAnalyzer
            .tokens(value)
            .find { offset >= it.start && offset < it.endExclusive }

        return offset until (containingToken?.endExclusive ?: (offset + 1))
    }


    // The in-scope step/binding names, keyed the way the analyzer keys an identifier token: each name escaped
    // to the Kotlin identifier an expression would reference it by, then reduced to its back-tick-free content.
    private fun knownIdentifiersOf(references: List<ObjectLocation>): Set<String> {
        return references.mapTo(mutableSetOf()) {
            ExpressionUtils.identifierContent(
                ExpressionUtils.escapeKotlinVariableName(it.objectPath.name.value))
        }
    }


    // The same in-scope set the insert-step-reference popover offers, so the two can never disagree about what
    // this expression may name. The label is the step's own name as the canvas shows it, while the inserted text
    // is that name escaped to a Kotlin identifier — escaping is lossy, so the two are not interchangeable. The
    // type is the one the validator resolved for that step, absent until its own validation settles.
    private fun completionsOf(
        references: List<ObjectLocation>,
        scriptValidation: ScriptValidation?
    ): List<CodeCompletion> {
        return references.map { reference ->
            val name = reference.objectPath.name.value

            val typeMetadata = scriptValidation
                ?.stepValidations
                ?.get(reference.objectPath)
                ?.typeMetadata
                ?.toSimple()
                ?.takeIf { it != voidTypeName }

            CodeCompletion(
                insertText = ExpressionUtils.escapeKotlinVariableName(name),
                label = name,
                detail = typeMetadata)
        }
    }
}
