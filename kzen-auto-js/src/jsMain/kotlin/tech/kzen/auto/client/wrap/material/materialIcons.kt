package tech.kzen.auto.client.wrap.material

import react.ChildrenBuilder
import react.ComponentType


external interface IconProps: react.Props {
    var title: String
    var style: react.CSSProperties?
    var onClick: () -> Unit
}


// see: https://mui.com/material-ui/material-icons/
//
// Each icon is a deep import of its own `@mui/icons-material/<Name>` module so the bundler includes
// ONLY the referenced icons. This replaces webpack's `require.context('@mui/icons-material', ...)`,
// which bundled the entire icon set (thousands of modules) — the source of most of the JS bundle's
// size and webpack's bundling time, and a webpack-only API that esbuild/Vite/Rollup cannot express.
//
// Icon names arriving at runtime (from notation `icon:` fields, via the dynamic iconByName/iconType
// call sites) are resolved through `iconRegistry`; an unknown name falls back to the Texture glyph —
// the same behaviour as before require.context was introduced.
//
// MAINTENANCE: the registry must contain every icon name referenced by (a) notation `icon:` fields
// under kzen-auto-jvm/src/main/resources/notation/**/*.yaml and (b) literal iconByName("X") /
// iconType("X") calls in this module. A name not listed here renders as Texture. A deep import of a
// non-existent `@mui/icons-material/<Name>` fails the BUILD (unlike require.context's runtime
// fallback), so every entry below corresponds to a real icon module. When adding a notation icon,
// add it here too. (External plugins that introduce their own icons are not covered — they get
// Texture, as in the pre-require.context era.)

@JsModule("@mui/icons-material/Texture")
external val muiTexture: dynamic

@JsModule("@mui/icons-material/Add")
external val muiAdd: dynamic
@JsModule("@mui/icons-material/AddBox")
external val muiAddBox: dynamic
@JsModule("@mui/icons-material/AddCircleOutline")
external val muiAddCircleOutline: dynamic
@JsModule("@mui/icons-material/ArrowBack")
external val muiArrowBack: dynamic
@JsModule("@mui/icons-material/ArrowDownward")
external val muiArrowDownward: dynamic
@JsModule("@mui/icons-material/ArrowForward")
external val muiArrowForward: dynamic
@JsModule("@mui/icons-material/ArrowForwardIos")
external val muiArrowForwardIos: dynamic
@JsModule("@mui/icons-material/CameraAlt")
external val muiCameraAlt: dynamic
@JsModule("@mui/icons-material/Cancel")
external val muiCancel: dynamic
@JsModule("@mui/icons-material/CancelPresentation")
external val muiCancelPresentation: dynamic
@JsModule("@mui/icons-material/Check")
external val muiCheck: dynamic
@JsModule("@mui/icons-material/Close")
external val muiClose: dynamic
@JsModule("@mui/icons-material/CloudDownload")
external val muiCloudDownload: dynamic
@JsModule("@mui/icons-material/Code")
external val muiCode: dynamic
@JsModule("@mui/icons-material/CompareArrows")
external val muiCompareArrows: dynamic
@JsModule("@mui/icons-material/Crop")
external val muiCrop: dynamic
@JsModule("@mui/icons-material/Delete")
external val muiDelete: dynamic
@JsModule("@mui/icons-material/DeviceHub")
external val muiDeviceHub: dynamic
@JsModule("@mui/icons-material/DragIndicator")
external val muiDragIndicator: dynamic
@JsModule("@mui/icons-material/Edit")
external val muiEdit: dynamic
@JsModule("@mui/icons-material/ExpandLess")
external val muiExpandLess: dynamic
@JsModule("@mui/icons-material/ExpandMore")
external val muiExpandMore: dynamic
@JsModule("@mui/icons-material/FileCopy")
external val muiFileCopy: dynamic
@JsModule("@mui/icons-material/FilterList")
external val muiFilterList: dynamic
@JsModule("@mui/icons-material/FolderOpen")
external val muiFolderOpen: dynamic
@JsModule("@mui/icons-material/Forward")
external val muiForward: dynamic
@JsModule("@mui/icons-material/Functions")
external val muiFunctions: dynamic
@JsModule("@mui/icons-material/GroupWork")
external val muiGroupWork: dynamic
@JsModule("@mui/icons-material/Http")
external val muiHttp: dynamic
@JsModule("@mui/icons-material/Input")
external val muiInput: dynamic
@JsModule("@mui/icons-material/Keyboard")
external val muiKeyboard: dynamic
@JsModule("@mui/icons-material/KeyboardArrowDown")
external val muiKeyboardArrowDown: dynamic
@JsModule("@mui/icons-material/LooksOne")
external val muiLooksOne: dynamic
@JsModule("@mui/icons-material/Mail")
external val muiMail: dynamic
@JsModule("@mui/icons-material/MenuBook")
external val muiMenuBook: dynamic
@JsModule("@mui/icons-material/Message")
external val muiMessage: dynamic
@JsModule("@mui/icons-material/MoreHoriz")
external val muiMoreHoriz: dynamic
@JsModule("@mui/icons-material/MoreVert")
external val muiMoreVert: dynamic
@JsModule("@mui/icons-material/OpenInNew")
external val muiOpenInNew: dynamic
@JsModule("@mui/icons-material/Pause")
external val muiPause: dynamic
@JsModule("@mui/icons-material/PlayArrow")
external val muiPlayArrow: dynamic
@JsModule("@mui/icons-material/PlaylistAdd")
external val muiPlaylistAdd: dynamic
@JsModule("@mui/icons-material/PlaylistPlay")
external val muiPlaylistPlay: dynamic
@JsModule("@mui/icons-material/PlusOne")
external val muiPlusOne: dynamic
@JsModule("@mui/icons-material/Print")
external val muiPrint: dynamic
@JsModule("@mui/icons-material/Public")
external val muiPublic: dynamic
@JsModule("@mui/icons-material/Redo")
external val muiRedo: dynamic
@JsModule("@mui/icons-material/Refresh")
external val muiRefresh: dynamic
@JsModule("@mui/icons-material/RemoveCircleOutline")
external val muiRemoveCircleOutline: dynamic
@JsModule("@mui/icons-material/Replay")
external val muiReplay: dynamic
@JsModule("@mui/icons-material/Save")
external val muiSave: dynamic
@JsModule("@mui/icons-material/SaveAlt")
external val muiSaveAlt: dynamic
@JsModule("@mui/icons-material/Search")
external val muiSearch: dynamic
@JsModule("@mui/icons-material/Send")
external val muiSend: dynamic
@JsModule("@mui/icons-material/Settings")
external val muiSettings: dynamic
@JsModule("@mui/icons-material/Share")
external val muiShare: dynamic
@JsModule("@mui/icons-material/Storage")
external val muiStorage: dynamic
@JsModule("@mui/icons-material/Stop")
external val muiStop: dynamic
@JsModule("@mui/icons-material/SubdirectoryArrowLeft")
external val muiSubdirectoryArrowLeft: dynamic
@JsModule("@mui/icons-material/SubdirectoryArrowRight")
external val muiSubdirectoryArrowRight: dynamic
@JsModule("@mui/icons-material/TableChart")
external val muiTableChart: dynamic
@JsModule("@mui/icons-material/Textsms")
external val muiTextsms: dynamic
@JsModule("@mui/icons-material/Timer")
external val muiTimer: dynamic
@JsModule("@mui/icons-material/ToggleOn")
external val muiToggleOn: dynamic
@JsModule("@mui/icons-material/TouchApp")
external val muiTouchApp: dynamic
@JsModule("@mui/icons-material/TripOrigin")
external val muiTripOrigin: dynamic
@JsModule("@mui/icons-material/Visibility")
external val muiVisibility: dynamic


private fun iconComponent(module: dynamic): ComponentType<IconProps> =
    module.default.unsafeCast<ComponentType<IconProps>>()


private val textureIcon: ComponentType<IconProps> = iconComponent(muiTexture)

private val iconRegistry: Map<String, ComponentType<IconProps>> = mapOf(
    "Add" to iconComponent(muiAdd),
    "AddBox" to iconComponent(muiAddBox),
    "AddCircleOutline" to iconComponent(muiAddCircleOutline),
    "ArrowBack" to iconComponent(muiArrowBack),
    "ArrowDownward" to iconComponent(muiArrowDownward),
    "ArrowForward" to iconComponent(muiArrowForward),
    "ArrowForwardIos" to iconComponent(muiArrowForwardIos),
    "CameraAlt" to iconComponent(muiCameraAlt),
    "Cancel" to iconComponent(muiCancel),
    "CancelPresentation" to iconComponent(muiCancelPresentation),
    "Check" to iconComponent(muiCheck),
    "Close" to iconComponent(muiClose),
    "CloudDownload" to iconComponent(muiCloudDownload),
    "Code" to iconComponent(muiCode),
    "CompareArrows" to iconComponent(muiCompareArrows),
    "Crop" to iconComponent(muiCrop),
    "Delete" to iconComponent(muiDelete),
    "DeviceHub" to iconComponent(muiDeviceHub),
    "DragIndicator" to iconComponent(muiDragIndicator),
    "Edit" to iconComponent(muiEdit),
    "ExpandLess" to iconComponent(muiExpandLess),
    "ExpandMore" to iconComponent(muiExpandMore),
    "FileCopy" to iconComponent(muiFileCopy),
    "FilterList" to iconComponent(muiFilterList),
    "FolderOpen" to iconComponent(muiFolderOpen),
    "Forward" to iconComponent(muiForward),
    "Functions" to iconComponent(muiFunctions),
    "GroupWork" to iconComponent(muiGroupWork),
    "Http" to iconComponent(muiHttp),
    "Input" to iconComponent(muiInput),
    "Keyboard" to iconComponent(muiKeyboard),
    "KeyboardArrowDown" to iconComponent(muiKeyboardArrowDown),
    "LooksOne" to iconComponent(muiLooksOne),
    "Mail" to iconComponent(muiMail),
    "MenuBook" to iconComponent(muiMenuBook),
    "Message" to iconComponent(muiMessage),
    "MoreHoriz" to iconComponent(muiMoreHoriz),
    "MoreVert" to iconComponent(muiMoreVert),
    "OpenInNew" to iconComponent(muiOpenInNew),
    "Pause" to iconComponent(muiPause),
    "PlayArrow" to iconComponent(muiPlayArrow),
    "PlaylistAdd" to iconComponent(muiPlaylistAdd),
    "PlaylistPlay" to iconComponent(muiPlaylistPlay),
    "PlusOne" to iconComponent(muiPlusOne),
    "Print" to iconComponent(muiPrint),
    "Public" to iconComponent(muiPublic),
    "Redo" to iconComponent(muiRedo),
    "Refresh" to iconComponent(muiRefresh),
    "RemoveCircleOutline" to iconComponent(muiRemoveCircleOutline),
    "Replay" to iconComponent(muiReplay),
    "Save" to iconComponent(muiSave),
    "SaveAlt" to iconComponent(muiSaveAlt),
    "Search" to iconComponent(muiSearch),
    "Send" to iconComponent(muiSend),
    "Settings" to iconComponent(muiSettings),
    "Share" to iconComponent(muiShare),
    "Storage" to iconComponent(muiStorage),
    "Stop" to iconComponent(muiStop),
    "SubdirectoryArrowLeft" to iconComponent(muiSubdirectoryArrowLeft),
    "SubdirectoryArrowRight" to iconComponent(muiSubdirectoryArrowRight),
    "TableChart" to iconComponent(muiTableChart),
    "Textsms" to iconComponent(muiTextsms),
    "Timer" to iconComponent(muiTimer),
    "ToggleOn" to iconComponent(muiToggleOn),
    "TouchApp" to iconComponent(muiTouchApp),
    "TripOrigin" to iconComponent(muiTripOrigin),
    "Visibility" to iconComponent(muiVisibility),
)


fun iconType(name: String): ComponentType<IconProps> =
    iconRegistry[name] ?: textureIcon


fun ChildrenBuilder.iconByName(name: String, block: IconProps.() -> Unit = {}) {
    iconType(name).invoke(block)
}
