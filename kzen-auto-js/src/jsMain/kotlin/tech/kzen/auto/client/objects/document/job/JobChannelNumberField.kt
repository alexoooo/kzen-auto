package tech.kzen.auto.client.objects.document.job

import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobChannelNumberFieldProps: Props {
    var label: String

    // The object holding the attribute. For the Job-defaults panel this is `main` (always present); for a
    // per-channel override it is the (possibly not-yet-materialized) synthesized-channel object.
    var objectLocation: ObjectLocation
    var attributeName: AttributeName

    // The value to display when [objectLocation] carries no explicit scalar for [attributeName] — the effective
    // default the parent resolved (Job-wide default, else archetype default).
    var fallbackValue: String

    // Materializes [objectLocation] (seeded with the effective defaults) before the first write, when it does
    // not yet exist; null when the object is always present (e.g. `main`). Invoked only on a genuine edit.
    var ensureObject: (suspend () -> Unit)?

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface JobChannelNumberFieldState: State {
    var value: String?
    var attributeNotation: AttributeNotation?
}


//---------------------------------------------------------------------------------------------------------------------
// A small labelled numeric field for a Job Channel's `batchSize` / `capacity`. Mirrors AttributePathValueEditor's
// observe-and-debounce shape (self-hydrates from notation, gates value updates on the observed attribute so
// typing is never clobbered by an unrelated re-render, flushes pending edits on unmount) but adds the two things
// the generic editor cannot: it falls back to a parent-supplied effective default when the object/attribute is
// absent, and it MATERIALIZES the object on the first genuine edit (create-on-edit) rather than on open. Like
// SelectClosePolicyEditor it writes ONLY on a real user change, so mount-time hydration is never echoed back as a
// no-op command.
class JobChannelNumberField(
    props: JobChannelNumberFieldProps
):
    RPureComponent<JobChannelNumberFieldProps, JobChannelNumberFieldState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun JobChannelNumberFieldState.init(props: JobChannelNumberFieldProps) {
        value = null
        attributeNotation = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        submitDebounce.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        val attributeNotation: AttributeNotation? =
            if (props.objectLocation in graphNotation.coalesce) {
                graphNotation.firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            }
            else {
                null
            }

        // Gate on the observed attribute (not every publish) so mid-edit typing isn't clobbered.
        if (state.attributeNotation == attributeNotation) {
            return
        }

        setState {
            this.attributeNotation = attributeNotation
            this.value = (attributeNotation as? ScalarAttributeNotation)?.value ?: props.fallbackValue
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        submitDebounce.apply()
    }


    // Write only on a genuine change — no componentDidUpdate write, so hydration is never echoed as a no-op.
    private suspend fun submitEdit() {
        val text = state.value
            ?: return
        val parsed = text.toIntOrNull()
            ?: return
        val canonical = parsed.toString()

        val current = (state.attributeNotation as? ScalarAttributeNotation)?.value
        if (canonical == current) {
            // Already this value.
            return
        }
        if (current == null && canonical == props.fallbackValue) {
            // Object/attribute absent and the user (re)typed the current effective default — no override needed.
            return
        }

        props.ensureObject?.invoke()
        props.mirroredGraphStore.apply(UpsertAttributeCommand(
            props.objectLocation, props.attributeName, ScalarAttributeNotation(canonical)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        TextField {
            fullWidth = true
            size = Size.small

            label = ReactNode(props.label)
            value = state.value ?: props.fallbackValue

            onChange = {
                val target = it.target as HTMLInputElement
                onValueChange(target.value)
            }

            // Commit any pending debounced edit the instant focus leaves the field (mirrors AttributePathValueEditor).
            onBlur = { submitDebounce.flush() }
        }
    }
}
