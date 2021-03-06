package tech.kzen.auto.client.wrap


// https://github.com/JetBrains/kotlin-wrappers/issues/61
//abstract class RPureComponent<P: RProps, S: RState>: PureComponent<P, S> {
//    constructor(): super() {
//        state = jsObject { init() }
//    }
//
//    constructor(props: P): super(props) {
//        state = jsObject { init(props) }
//    }
//
//
//    open fun S.init() {}
//    // if you use this method, don't forget to pass props to the constructor first
//    open fun S.init(props: P) {}
//
//
//    fun RBuilder.children() {
//        props.children()
//    }
//
//
//    abstract fun RBuilder.render()
//
//    override fun render() = buildElements { render() }
//}
