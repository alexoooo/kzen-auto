package tech.kzen.auto.client.objects.document.common.scope


import react.Props
import react.State
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.location.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
/**
 * The addressing pair every ClientState-observing document component shares: the object it renders, and the
 * broadcast it listens to. Extracted so [ObjectScopedComponent] can hold the stale-location contract once
 * instead of each props subtype re-declaring these two fields (AttributeEditorProps, AttributeViewProps and
 * the signature editors' props each did).
 */
external interface ObjectScopedProps: Props {
    var objectLocation: ObjectLocation
    var clientStateGlobal: ClientStateGlobal
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A component rendering one object's notation, subscribed to [ClientStateGlobal] for its lifetime.
 *
 * Exists to make the stale-`ObjectLocation` hazard unforgettable rather than remembered. A publish runs before
 * React re-renders, so `props.objectLocation` can point at a step that was just deleted or renamed, and any
 * notation lookup on it throws `IllegalArgumentException("Missing: ...")`. Declaring the scope here — `final`,
 * so no subclass can weaken it — makes `ClientStateGlobal.deliver` skip those broadcasts, and the subclass's
 * [onClientState] body never has to guard. See `docs/js-architecture.md`.
 *
 * Subclasses observing further stores override the lifecycle hooks and call `super`; the common case inherits
 * both and writes neither.
 */
abstract class ObjectScopedComponent<P: ObjectScopedProps, S: State>:
    RPureComponent<P, S>,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    constructor(): super()

    constructor(props: P): super(props)


    //-----------------------------------------------------------------------------------------------------------------
    final override fun observedObjectLocation(): ObjectLocation =
        props.objectLocation


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }
}
