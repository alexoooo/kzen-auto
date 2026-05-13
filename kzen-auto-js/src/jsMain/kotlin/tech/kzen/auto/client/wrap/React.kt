package tech.kzen.auto.client.wrap


import js.objects.unsafeJso
import js.reflect.unsafeCast
import mui.material.InputBaseProps
import mui.material.TextFieldProps
import react.*
import kotlin.reflect.KClass


abstract class RComponent<P : Props, S : State> : Component<P, S> {
    constructor() : super() {
        state = unsafeJso { init() }
    }

    constructor(props: P) : super(props) {
        state = unsafeJso { init(props) }
    }

    open fun S.init() {}

    // if you use this method, don't forget to pass props to the constructor first
    open fun S.init(props: P) {}

    abstract fun ChildrenBuilder.render()

    override fun render(): ReactNode = Fragment.create { render() }
}


fun <S : State> Component<*, S>.setState(buildState: S.() -> Unit) {
    val partialState: S = unsafeJso {
        buildState()
    }
    setState(partialState)
}


// Replaces react.PureComponent (removed in kotlin-wrappers 2026.x).
// React's PureComponent is documented as Component + shouldComponentUpdate that
// shallow-compares props and state — that's all this re-implements.
abstract class RPureComponent<P : Props, S : State> : Component<P, S> {
    constructor() : super() {
        state = unsafeJso { init() }
    }

    constructor(props: P) : super(props) {
        state = unsafeJso { init(props) }
    }

    open fun S.init() {}

    open fun S.init(props: P) {}

    abstract fun ChildrenBuilder.render()

    override fun render(): ReactNode = Fragment.create { render() }

    override fun shouldComponentUpdate(nextProps: P, nextState: S): Boolean =
        !shallowEqual(props, nextProps) || !shallowEqual(state, nextState)
}


// React's own shallowEqual uses Object.is per key; we use === for simplicity.
// The NaN/±0 edge cases that distinguish them don't surface in props/state in practice.
private fun shallowEqual(a: Any?, b: Any?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    val keysA = js("Object.keys")(a).unsafeCast<Array<String>>()
    val keysB = js("Object.keys")(b).unsafeCast<Array<String>>()
    if (keysA.size != keysB.size) return false
    for (key in keysA) {
        if (a.asDynamic()[key] !== b.asDynamic()[key]) return false
    }
    return true
}


// Replaces the `react.react` KClass extension that was removed in kotlin-wrappers 2026.x
// (it lived in `kotlin-react-legacy`). Bridges a class-component KClass to the modern
// ElementType so call sites can keep using `SomeComponent::class.react { ... }`.
inline val <P : Props> KClass<out Component<P, *>>.react: ComponentType<P>
    get() = unsafeCast(js)


// Replaces react.createRef (removed in kotlin-wrappers 2026.x — only `useRef` remains, but
// that's a hook and class components can't call hooks). Class components instantiate
// a RefObject directly; React's createRef was just `{ current: null }` as a plain object.
fun <T : Any> createRef(): RefObject<T> = unsafeJso { current = null }


inline var TextFieldProps.InputProps: InputBaseProps
    get() = TODO("Prop is write-only!")
    set(value) {
        asDynamic().InputProps = value
    }
