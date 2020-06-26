@file:JsModule("@material-ui/core")
package tech.kzen.auto.client.wrap

import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.Component
import react.RProps
import react.RState
import react.ReactElement
import kotlin.js.Json


// also see: https://github.com/rivasdiaz/kotlin-rmwc

@JsName("Button")
external class MaterialButton: Component<MaterialButtonProps, RState> {
    override fun render(): ReactElement?
}

external interface MaterialButtonProps: RProps {
    var id: String
    var title: String
    var variant: String
    var color: String
    var style: Json
    var size: String
    var fullWidth: Boolean

    var onClick: () -> Unit
    var onMouseOver: () -> Unit
    var onMouseOut: () -> Unit

    var buttonRef: (e: HTMLButtonElement?) -> Unit
}



@JsName("IconButton")
external class MaterialIconButton: Component<MaterialIconButtonProps, RState> {
    override fun render(): ReactElement?
}

external interface MaterialIconButtonProps: RProps {
//    var id: String
//    var variant: String
    var title: String

    var disabled: Boolean

//    var size: String
    var color: String
    var style: Json

    var onClick: () -> Unit
    var onMouseOver: () -> Unit
    var onMouseOut: () -> Unit

    var buttonRef: (e: HTMLButtonElement?) -> Unit
}


@JsName("Fab")
external class MaterialFab: Component<MaterialButtonProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Drawer")
external class MaterialDrawer: Component<DrawerProps, RState> {
    override fun render(): ReactElement?
}

external interface DrawerProps: RProps {
    var variant: String
    var style: Json
}


// https://stackoverflow.com/a/37332913/1941359
@JsName("Tabs")
external class MaterialTabs: Component<TabsProps, RState> {
    override fun render(): ReactElement?
}

external interface TabsProps: RProps {
    var variant: String
    var indicatorColor: String
    var textColor: String
    var value: Int

    var onChange: (Any, Int) -> Unit

    var style: Json
}


@JsName("Tab")
external class MaterialTab: Component<TabProps, RState> {
    override fun render(): ReactElement?
}

external interface TabProps: RProps {
    var label: String
    var style: Json
}


@JsName("Typography")
external class MaterialTypography: Component<TypographyProps, RState> {
    override fun render(): ReactElement?
}
external interface TypographyProps: RProps {
    var style: Json
}



@JsName("AppBar")
external class MaterialAppBar: Component<AppBarProps, RState> {
    override fun render(): ReactElement?
}


external interface AppBarProps: RProps {
    var position: String
    var style: Json
}


@JsName("Toolbar")
external class MaterialToolbar: Component<RProps, RState> {
    override fun render(): ReactElement?
}




@JsName("Card")
external class MaterialCard: Component<CardProps, RState> {
    override fun render(): ReactElement?
}



external interface CardProps: RProps {
    var style: Json

//    var classes: Json
//    var className: String

    var raised: Boolean
}



@JsName("CardContent")
external class MaterialCardContent: Component<RProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CardActions")
external class MaterialCardActions: Component<RProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Paper")
external class MaterialPaper: Component<PaperProps, RState> {
    override fun render(): ReactElement?
}


external interface PaperProps: RProps {
    var style: Json
}


@JsName("TextField")
external class MaterialTextField: Component<TextFieldProps, RState> {
    override fun render(): ReactElement?
}

external interface TextFieldProps: RProps {
    var style: Json

    var onChange: (e: Event) -> Unit

    var onKeyDown: (e: KeyboardEvent) -> Unit
    var onKeyPress: (e: KeyboardEvent) -> Unit

    var autoFocus: Boolean
    var inputRef: (e: HTMLInputElement?) -> Unit

    var InputLabelProps: NestedInputLabelProps

    var id: String
    var value: String
    var label: String
    var rows: Int
    var multiline: Boolean
    var fullWidth: Boolean
    var margin: String
    var disabled: Boolean
    var error: Boolean
}


@JsName("InputLabel")
external class MaterialInputLabel : Component<MaterialInputLabelProps, RState> {
    override fun render(): ReactElement?
}

external interface MaterialInputLabelProps : RProps {
    var id: String
    var htmlFor: String
    var style: Json
}


@JsName("FormControl")
external class MaterialFormControl : Component<RProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Switch")
external class MaterialSwitch: Component<SwitchProps, RState> {
    override fun render(): ReactElement?
}

external interface SwitchProps: RProps {
//    var style: Json

    var id: String

    var checked: Boolean
    var onChange: (e: Event) -> Unit

//    var onKeyDown: (e: KeyboardEvent) -> Unit
//    var onKeyPress: (e: KeyboardEvent) -> Unit
//
//    var autoFocus: Boolean
//    var inputRef: (e: HTMLInputElement?) -> Unit
//
//    var InputLabelProps: NestedInputLabelProps
//
//    var id: String
//
//    var label: String
//    var rows: Int
//    var multiline: Boolean
//    var fullWidth: Boolean
//    var margin: String
}

//@JsName("FormControl")
//external class MaterialFormControl : Component<MaterialFormControlProps, RState> {
//    override fun render(): ReactElement?
//}
//
//external interface MaterialFormControlProps : RProps {
//}

@JsName("Select")
external class MaterialSelect : Component<MaterialSelectProps, RState> {
    override fun render(): ReactElement?
}

external interface MaterialSelectProps : RProps {
    var id: String
    var labelId: String
    var style: Json

    var value: String
    var onChange: (Event) -> Unit

    var inputProps: Json
}


@JsName("Menu")
external class MaterialMenu: Component<MenuProps, RState> {
    override fun render(): ReactElement?
}


external interface MenuProps: RProps {
    var open: Boolean
    var onClose: () -> Unit
    var anchorEl: HTMLElement?
}


@JsName("MenuItem")
external class MaterialMenuItem: Component<MenuItemProps, RState> {
    override fun render(): ReactElement?
}

external interface MenuItemProps: RProps {
    var onClick: () -> Unit
    var value: String
}



@JsName("Checkbox")
external class MaterialCheckbox: Component<CheckboxProps, RState> {
    override fun render(): ReactElement?
}

external interface CheckboxProps: RProps {
//    var style: Json

    var id: String

    var checked: Boolean
    var onChange: (e: Event) -> Unit

    var disabled: Boolean
}


@JsName("CircularProgress")
external class MaterialCircularProgress: Component<RProps, RState> {
    override fun render(): ReactElement?
}
