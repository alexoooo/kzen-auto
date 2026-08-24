package tech.kzen.auto.client.objects.document.common.edit.select

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SelectReferenceEditorState: State {
    // The selected option's KEY - i.e. SelectOption.value, NOT the string written to notation. The two differ
    // wherever the wire form is a cropped reference; wireValue() maps key -> wire at commit time.
    var selected: String?

    // null = candidates not computed yet, so render nothing.
    var options: Array<SelectOption>?

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
// Shared skeleton for the attribute editors that pick an object reference from a computed candidate list
// (step / channel / object / logic / enclosing-loop). It owns the graph-store subscription, the commit, and the
// autocomplete render; subclasses supply the candidates, the crop policy, and their notation-event handling.
//
// Write discipline - the invariant this base exists to enforce: the ONLY path that writes notation is
// [selectAndCommit], reached from the field's onSelect (plus one deliberate call for a default pre-fill).
// Hydration and rename tracking go through [setSelected], which never writes. Before this base each editor
// committed from componentDidUpdate on any value change, guarded by ad-hoc `renaming` / `initialized` flags
// that had to distinguish "the user picked this" from "the notation moved underneath us" - which also meant an
// external edit could round-trip straight back out as a redundant write.
abstract class SelectReferenceEditorBase<P: AttributeEditorProps, S: SelectReferenceEditorState>(
    props: P
):
    RPureComponent<P, S>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    // NB: no `S.init(props)` here on purpose. Every field above is nullable, so an unset external-interface slot
    // already reads as null, and leaving init to the subclass keeps synchronous hydration (reading
    // clientStateGlobal.current() during construction) available to those that want it. An open member called
    // from the constructor would run before subclass property initializers anyway.


    //-----------------------------------------------------------------------------------------------------------------
    // The selection always carries its own value, so there is no pending buffer to read: schedule/flush are never
    // called and only the explicit-value commitNow is used.
    //
    // NB: `this.props`, not bare `props` - in a property initializer the constructor parameter shadows the
    // inherited member, so a bare reference would pin the FIRST render's props object for the component's whole
    // life. These editors outlive a rename of their own host (the manager re-renders them with a new
    // objectLocation), and a commit must target the current one.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = { null },
        onError = { message -> setState { errorMessage = message } },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    // Installed in the base so every subclass reaches its document's edit-activity via the committer; idempotent
    // with subclasses that also install it for their own bridge lookups.
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false


    final override fun componentDidMount() {
        mounted = true
        // NB: observe is suspending, hence the async - keep this ordering, subclass observers attach after.
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
        onMount()
    }


    final override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
        onUnmount()
    }


    // Attach / detach any additional observers (ClientStateGlobal, ScriptStore, ...) here rather than overriding
    // the lifecycle methods, so no subclass has to remember a super call.
    protected open fun onMount() {}


    protected open fun onUnmount() {}


    //-----------------------------------------------------------------------------------------------------------------
    final override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        onNotationEvent(event, graphDefinition)
    }


    final override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    // React to a committed notation change: adopt a rename via [setSelected], and/or recompute the candidates.
    protected abstract suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt)


    //-----------------------------------------------------------------------------------------------------------------
    // State only - never writes. For hydrating from notation and for following a rename.
    protected fun setSelected(optionKey: String?) {
        if (state.selected == optionKey) {
            return
        }

        setState {
            selected = optionKey
        }
    }


    // State AND write. The single notation-write path of this family.
    protected fun selectAndCommit(optionKey: String) {
        setState {
            selected = optionKey
        }

        async {
            committer.commitNow(ScalarAttributeNotation(wireValue(optionKey)))
        }
    }


    // Content compare before setState: SelectOption is an external interface and Array equality is by reference,
    // so a freshly rebuilt but identical candidate list would otherwise defeat RPureComponent's shallow bail-out.
    protected fun setOptions(options: Array<SelectOption>) {
        val current = state.options
        if (current != null && current.size == options.size &&
                options.indices.all {
                    current[it].value == options[it].value &&
                        current[it].label == options[it].label &&
                        current[it].detail == options[it].detail &&
                        current[it].detailTitle == options[it].detailTitle &&
                        current[it].group == options[it].group
                }
        ) {
            return
        }

        setState {
            this.options = options
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The notation form of a selected option key - identity where the key is already the wire string, a cropped
    // reference where the editor writes document-relative.
    protected abstract fun wireValue(optionKey: String): String


    protected open fun label(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // `open` / `onOpen` / `onClose` are for subclasses that attach a meaning to the dropdown being open (see
    // StepPickingSelectEditorBase, where it arms a canvas pick session); left at their defaults the field
    // keeps MUI's own uncontrolled toggle. The close reason isn't forwarded — no subclass needs to tell
    // Escape from a click-away, they all just end whatever the open state meant.
    protected fun ChildrenBuilder.selectField(
        options: Array<SelectOption>,
        open: Boolean? = null,
        onOpen: (() -> Unit)? = null,
        onClose: (() -> Unit)? = null
    ) {
        muiAutocompleteField(
            label = label(),
            options = options,
            selectedOption = options.find { it.value == state.selected },
            onSelect = { selectAndCommit(it.value) },
            error = state.errorMessage != null,
            disableClearable = true,
            open = open,
            onOpen = onOpen,
            onClose = onClose?.let { callback -> { _ -> callback() } })
    }


    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        selectField(options)
    }
}
