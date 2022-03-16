@file:JsModule("@material-ui/core")
package tech.kzen.auto.client.wrap.material

import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.Component
import react.ReactElement
import kotlin.js.Json


// see: https://github.com/rivasdiaz/kotlin-rmwc
// see: https://github.com/cfnz/muirwik

//---------------------------------------------------------------------------------------------------------------------
@JsName("Button")
external class MaterialButton: Component<MaterialButtonProps, react.State> {
    override fun render(): ReactElement<MaterialButtonProps>?
}

external interface MaterialButtonProps: react.Props {
    var id: String
    var title: String
    var variant: String
    var color: String
    var style: Json
    var size: String
    var fullWidth: Boolean
    var disabled: Boolean
    var disableElevation: Boolean

    var onClick: () -> Unit
    var onMouseOver: () -> Unit
    var onMouseOut: () -> Unit

    var buttonRef: (e: HTMLButtonElement?) -> Unit
}



@JsName("IconButton")
external class MaterialIconButton: Component<MaterialIconButtonProps, react.State> {
    override fun render(): ReactElement<MaterialIconButtonProps>?
}

external interface MaterialIconButtonProps: react.Props {
//    var id: String
//    var variant: String
    var title: String

    var disabled: Boolean

    var size: String
    var color: String
    var style: Json

    var onClick: () -> Unit
    var onMouseOver: () -> Unit
    var onMouseOut: () -> Unit

    var buttonRef: (e: HTMLButtonElement?) -> Unit
}


@JsName("Fab")
external class MaterialFab: Component<MaterialButtonProps, react.State> {
    override fun render(): ReactElement<MaterialButtonProps>?
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("Drawer")
external class MaterialDrawer: Component<DrawerProps, react.State> {
    override fun render(): ReactElement<DrawerProps>?
}

external interface DrawerProps: react.Props {
    var variant: String
    var style: Json
}


// https://stackoverflow.com/a/37332913/1941359
@JsName("Tabs")
external class MaterialTabs: Component<TabsProps, react.State> {
    override fun render(): ReactElement<TabsProps>?
}

external interface TabsProps: react.Props {
    var variant: String
    var indicatorColor: String
    var textColor: String
    var value: Int

    var onChange: (Any, Int) -> Unit

    var style: Json
}


@JsName("Tab")
external class MaterialTab: Component<TabProps, react.State> {
    override fun render(): ReactElement<TabProps>?
}

external interface TabProps: react.Props {
    var label: String
    var style: Json
}


@JsName("Typography")
external class MaterialTypography: Component<TypographyProps, react.State> {
    override fun render(): ReactElement<TypographyProps>?
}

external interface TypographyProps: react.Props {
    var style: Json
}


@JsName("AppBar")
external class MaterialAppBar: Component<AppBarProps, react.State> {
    override fun render(): ReactElement<AppBarProps>?
}


external interface AppBarProps: react.Props {
    var position: String
    var style: Json
}


@JsName("Toolbar")
external class MaterialToolbar: Component<react.Props, react.State> {
    override fun render(): ReactElement<react.Props>?
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("Card")
external class MaterialCard: Component<CardProps, react.State> {
    override fun render(): ReactElement<CardProps>?
}


external interface CardProps: react.Props {
    var style: Json

//    var classes: Json
//    var className: String

    var raised: Boolean
}


@JsName("CardContent")
external class MaterialCardContent: Component<react.Props, react.State> {
    override fun render(): ReactElement<react.Props>?
}


@JsName("CardActions")
external class MaterialCardActions: Component<react.Props, react.State> {
    override fun render(): ReactElement<react.Props>?
}


@JsName("Paper")
external class MaterialPaper: Component<PaperProps, react.State> {
    override fun render(): ReactElement<PaperProps>?
}


external interface PaperProps: react.Props {
    var style: Json
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("TextField")
external class MaterialTextField: Component<TextFieldProps, react.State> {
    override fun render(): ReactElement<TextFieldProps>?
}

external interface TextFieldProps: react.Props {
    var style: Json

    var onChange: (e: Event) -> Unit

    var onKeyDown: (e: KeyboardEvent) -> Unit
    var onKeyPress: (e: KeyboardEvent) -> Unit

    var autoFocus: Boolean
    var inputRef: (e: HTMLInputElement?) -> Unit

//    var InputProps: NestedInputProps
    var InputProps: react.Props
    var InputLabelProps: NestedInputLabelProps

    var id: String
    var value: String
    var label: String
    var size: String
    var rows: Int
    var maxRows: Int
    var multiline: Boolean
    var fullWidth: Boolean
    var margin: String
    var disabled: Boolean
    var error: Boolean
    var type: String
}


//external class NestedInputProps : Component<MaterialInputLabelProps, react.State> {
//    override fun render(): ReactElement?
//}


@JsName("InputAdornment")
external class MaterialInputAdornment : Component<MaterialInputAdornmentProps, react.State> {
    override fun render(): ReactElement<MaterialInputAdornmentProps>?
}


external interface MaterialInputAdornmentProps : react.Props {
    var position: String
}


@JsName("InputLabel")
external class MaterialInputLabel : Component<MaterialInputLabelProps, react.State> {
    override fun render(): ReactElement<MaterialInputLabelProps>?
}

external interface MaterialInputLabelProps : react.Props {
    var id: String
    var htmlFor: String
    var style: Json
}


@JsName("FormControl")
external class MaterialFormControl : Component<react.Props, react.State> {
    override fun render(): ReactElement<react.Props>?
}


@JsName("Switch")
external class MaterialSwitch: Component<SwitchProps, react.State> {
    override fun render(): ReactElement<react.Props>?
}


external interface SwitchProps: react.Props {
//    var style: Json

    var id: String

    var checked: Boolean
    var onChange: (e: Event) -> Unit

    var disabled: Boolean

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
//external class MaterialFormControl : Component<MaterialFormControlProps, react.State> {
//    override fun render(): ReactElement?
//}
//
//external interface MaterialFormControlProps : react.Props {
//}

@JsName("Select")
external class MaterialSelect : Component<MaterialSelectProps, react.State> {
    override fun render(): ReactElement<MaterialSelectProps>?
}

external interface MaterialSelectProps: react.Props {
    var id: String
    var labelId: String
    var style: Json

    var value: String
    var onChange: (Event) -> Unit

    var inputProps: Json
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("Menu")
external class MaterialMenu: Component<MenuProps, react.State> {
    override fun render(): ReactElement<MenuProps>?
}


external interface MenuProps: react.Props {
    var open: Boolean
    var onClose: () -> Unit
    var anchorEl: HTMLElement?
}


@JsName("MenuItem")
external class MaterialMenuItem: Component<MenuItemProps, react.State> {
    override fun render(): ReactElement<MenuItemProps>?
}


external interface MenuItemProps: react.Props {
    var onClick: () -> Unit
    var value: String
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("Checkbox")
external class MaterialCheckbox: Component<CheckboxProps, react.State> {
    override fun render(): ReactElement<CheckboxProps>?
}

external interface CheckboxProps: react.Props {
    var style: Json

    var id: String
    var value: String

    var checked: Boolean
    var indeterminate: Boolean
    var onChange: (e: Event) -> Unit

    var disabled: Boolean
    var disableRipple: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("CircularProgress")
external class MaterialCircularProgress: Component<ProgressProps, react.State> {
    override fun render(): ReactElement<ProgressProps>?
}


@JsName("LinearProgress")
external class MaterialLinearProgress: Component<ProgressProps, react.State> {
    override fun render(): ReactElement<ProgressProps>?
}


external interface ProgressProps: react.Props {
    var style: Json
    var classes: Json
}


//---------------------------------------------------------------------------------------------------------------------
@JsName("Fade")
external class MaterialFade: Component<FadeProps, react.State> {
    override fun render(): ReactElement<FadeProps>?
}

external interface FadeProps: react.Props {
    var `in`: Boolean
    var timeout: FadeTimeout
}
//
//external interface FadeTimeout {
//    var appear: Int
//    var enter: Int
//    var exit: Int
//}