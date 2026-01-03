package tech.kzen.auto.client.wrap


import js.objects.unsafeJso
import mui.material.InputBaseProps
import mui.material.TextFieldProps
import react.*


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


abstract class RPureComponent<P : Props, S : State> : PureComponent<P, S> {
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


fun <S : State> PureComponent<*, S>.setState(buildState: S.() -> Unit) {
    val partialState: S = unsafeJso {
        buildState()
    }
    setState(partialState)
}



inline var TextFieldProps.InputProps: InputBaseProps
    get() = TODO("Prop is write-only!")
    set(value) {
        asDynamic().InputProps = value
    }