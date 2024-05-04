package tech.kzen.auto.client.wrap.select


external interface ReactSelectOption {
    var value: String
    var label: String
}


//fun RBuilder.materialReactSelectController(props: react.Props): ReactElement =
//        child(MaterialTextField::class) {
//            attrs.name = name
//        }
//
//class MaterialReactSelect : Component<ReactSelectProps, react.State> {
//    override fun render(): ReactElement?
//}