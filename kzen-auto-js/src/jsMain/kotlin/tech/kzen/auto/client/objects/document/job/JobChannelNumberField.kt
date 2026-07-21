package tech.kzen.auto.client.objects.document.job

import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.DebouncedSubmitter
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobChannelNumberFieldProps: Props {
    var label: String

    // The object holding the attribute — always an existing object: `main` for the Job-wide defaults panel, or
    // the upstream Worker for a per-channel config field.
    var objectLocation: ObjectLocation

    // The path to the value: a top-level name (`main.batchSize`, the Job-wide default) OR a nested path
    // (`channels.<port>.batchSize` on a Worker). The path SHAPE also picks the semantics: a nested leaf can be
    // left blank to inherit the effective default (and cleared to revert); a top-level attribute is the base of
    // the precedence with no removal command, so it always shows a value.
    var attributePath: AttributePath

    // The effective default shown when [objectLocation] carries no own value at [attributePath] — as a greyed
    // placeholder for a nested (inheritable) field, or as the displayed value for a top-level one.
    var fallbackValue: String

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface JobChannelNumberFieldState: State {
    var value: String?

    // The object's OWN (not inheritance-resolved) notation at the path: non-null = an explicit override,
    // null = inheriting. Gates value updates so mid-edit typing is never clobbered by an unrelated re-render.
    var ownNotation: AttributeNotation?

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
// A small labelled numeric field for a Job channel's `batchSize` / `capacity`. Mirrors AttributePathValueEditor's
// observe-and-debounce shape (self-hydrates from notation, gates value updates on the observed value so typing is
// never clobbered, flushes pending edits on unmount), but distinguishes an explicit OVERRIDE from an inherited
// default: it reads the object's OWN value (not inheritance-resolved), so a nested per-channel field renders BLANK
// with the effective default as a greyed placeholder while inheriting, and shows a solid value once overridden.
// Clearing an overridden nested field reverts it to inheriting (removes the override). A top-level field
// (`main.batchSize`, the Job-wide default) has no lower-precedence default to revert to and no attribute-removal
// command, so it always shows a value. Like SelectValuesEditor it writes ONLY on a real user change, so
// mount-time hydration is never echoed back as a no-op command.
class JobChannelNumberField(
    props: JobChannelNumberFieldProps
):
    RPureComponent<JobChannelNumberFieldProps, JobChannelNumberFieldState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun JobChannelNumberFieldState.init(props: JobChannelNumberFieldProps) {
        value = null
        ownNotation = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        submitter.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val submitter = DebouncedSubmitter { submitEdit() }


    // Blank means "inherit the effective default" only for a nested per-channel knob (`channels.<port>.<knob>`,
    // revertible via RemoveInAttributeCommand). A top-level Job-wide default is the base of the precedence and
    // has no attribute-removal command, so it is never left blank.
    private fun inheritable(): Boolean {
        return props.attributePath.nesting.segments.isNotEmpty()
    }


    // The object's OWN value at the path (NOT inheritance-resolved), so an inheriting field reads null rather
    // than the ancestor's value. Presence here — regardless of whether it equals the default — is what makes the
    // value an explicit override.
    private fun readOwnNotation(graphNotation: GraphNotation): AttributeNotation? {
        val objectNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?.objects?.notations?.map?.get(props.objectLocation.objectPath)
            ?: return null
        return objectNotation.get(props.attributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        val own: AttributeNotation? =
            if (props.objectLocation in graphNotation.coalesce) {
                readOwnNotation(graphNotation)
            }
            else {
                null
            }

        // Gate on the observed own value (not every publish) so mid-edit typing isn't clobbered.
        if (state.ownNotation == own) {
            return
        }

        setState {
            this.ownNotation = own
            this.value = (own as? ScalarAttributeNotation)?.value?.ifBlank { null }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        submitter.schedule()
    }


    // Write only on a genuine change — no componentDidUpdate write, so hydration is never echoed as a no-op.
    // Clearing an overridden nested field reverts it to inheriting.
    private suspend fun submitEdit() {
        val text = state.value?.trim().orEmpty()
        val currentOverride = (state.ownNotation as? ScalarAttributeNotation)?.value?.ifBlank { null }

        if (text.isEmpty()) {
            // Cleared → revert to the inherited default by removing the override. Only a nested leaf has a
            // removal command (and a lower-precedence default to fall back to); a top-level default stays.
            if (currentOverride != null && inheritable()) {
                applyCommand(RemoveInAttributeCommand(
                    props.objectLocation, props.attributePath, true))
            }
            return
        }

        val canonical = text.toIntOrNull()?.toString()
            ?: return
        if (canonical == currentOverride) {
            // Already this override.
            return
        }

        applyCommand(writeCommand(ScalarAttributeNotation(canonical)))
    }


    // Only a genuinely applied command updates the error state; the parse / no-change bails above leave it alone.
    private suspend fun applyCommand(command: NotationCommand) {
        val errorMessage = CommonEditUtils.applyCommand(props.mirroredGraphStore, command)
        setState {
            this.errorMessage = errorMessage
        }
    }


    // Persist [value] at [attributePath], picking the command by path shape: a top-level attribute is a plain
    // upsert; a nested leaf is updated in place when present, or inserted (creating the intermediate
    // `channels.<port>` maps) on first override.
    private fun writeCommand(value: ScalarAttributeNotation): NotationCommand {
        val attributePath = props.attributePath
        return when {
            attributePath.nesting.segments.isEmpty() ->
                UpsertAttributeCommand(props.objectLocation, attributePath.attribute, value)

            state.ownNotation != null ->
                UpdateInAttributeCommand(props.objectLocation, attributePath, value)

            else ->
                InsertMapEntryInAttributeCommand(
                    props.objectLocation,
                    attributePath.parent(),
                    PositionRelation.afterLast,
                    attributePath.nesting.segments.last(),
                    value,
                    true)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val inheritable = inheritable()

        TextField {
            fullWidth = true
            size = Size.small

            label = ReactNode(props.label)

            if (inheritable) {
                // Blank while inheriting: the effective default shows as a greyed placeholder, so an overridden
                // value (solid text) reads distinctly from an inherited one.
                value = state.value ?: ""
                placeholder = props.fallbackValue
            }
            else {
                value = state.value?.ifBlank { null } ?: props.fallbackValue
            }

            onChange = {
                val target = it.target as HTMLInputElement
                onValueChange(target.value)
            }

            // Commit any pending debounced edit the instant focus leaves the field (mirrors AttributePathValueEditor).
            onBlur = { submitter.flush() }

            error = state.errorMessage != null
        }
    }
}
