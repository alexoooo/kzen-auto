@file:JsModule("@mui/icons-material")
package tech.kzen.auto.client.wrap.material


import react.Component
import react.ReactElement


// see: https://material-ui.com/style/icons/
// see: https://material.io/tools/icons/?style=baseline


// NB: can't create common MaterialIcon interface because 'external' doesn't support that

external interface IconProps: react.Props {
    var title: String
//    var color: String

//    var style: Json
    var style: react.CSSProperties?

    var onClick: () -> Unit
}


@JsName("Delete")
external class DeleteIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("PlayArrow")
external class PlayArrowIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Replay")
external class ReplayIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("KeyboardArrowUp")
external class KeyboardArrowUpIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("KeyboardArrowDown")
external class KeyboardArrowDownIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Save")
external class SaveIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("SaveAlt")
external class SaveAltIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Cancel")
external class CancelIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CancelPresentation")
external class CancelPresentationIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Close")
external class CloseIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}

@JsName("Clear")
external class ClearIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ArrowDownward")
external class ArrowDownwardIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("SubdirectoryArrowRight")
external class SubdirectoryArrowRightIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ArrowForward")
external class ArrowForwardIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ArrowForwardIos")
external class ArrowForwardIosIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ArrowBack")
external class ArrowBackIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("OpenInNew")
external class OpenInNewIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Http")
external class HttpIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Keyboard")
external class KeyboardIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("DeviceHub")
external class DeviceHubIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("TouchApp")
external class TouchAppIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Message")
external class MessageIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Texture")
external class TextureIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Timer")
external class TimerIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Edit")
external class EditIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Pause")
external class PauseIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Send")
external class SendIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Textsms")
external class TextsmsIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Input")
external class InputIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("LooksOne")
external class LooksOneIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("MoreVert")
external class MoreVertIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("MoreHoriz")
external class MoreHorizIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("SubdirectoryArrowLeft")
external class SubdirectoryArrowLeftIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Folder")
external class FolderIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("FolderOpen")
external class FolderOpenIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Receipt")
external class ReceiptIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("PlaylistPlay")
external class PlaylistPlayIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("FormatListNumbered")
external class FormatListNumberedIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("TableChart")
external class TableChartIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Search")
external class SearchIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CallSplit")
external class CallSplitIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("TripOrigin")
external class TripOriginIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("PlusOne")
external class PlusOneIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("SettingsInputComponent")
external class SettingsInputComponentIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("FilterList")
external class FilterListIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Forward")
external class ForwardIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("FileCopy")
external class FileCopyIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Add")
external class AddIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("AddCircleOutline")
external class AddCircleOutlineIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Remove")
external class RemoveIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("RemoveCircle")
external class RemoveCircleIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("RemoveCircleOutline")
external class RemoveCircleOutlineIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CompareArrows")
external class CompareArrowsIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("PlaylistAdd")
external class PlaylistAddIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Print")
external class PrintIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Redo")
external class RedoIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Mail")
external class MailIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ToggleOn")
external class ToggleOnIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Flip")
external class FlipIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("AddBox")
external class AddBoxIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Share")
external class ShareIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Crop")
external class CropIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CameraAlt")
external class CameraAltIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Refresh")
external class RefreshIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("TransitEnterexit")
external class TransitEnterexitIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("KeyboardReturn")
external class KeyboardReturnIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ExpandLess")
external class ExpandLessIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ExpandMore")
external class ExpandMoreIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Scanner")
external class ScannerIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("MenuBook")
external class MenuBookIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Stop")
external class StopIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("PlayForWork")
external class PlayForWorkIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Error")
external class ErrorIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ErrorOutline")
external class ErrorOutlineIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Pageview")
external class PageviewIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CloudDownload")
external class CloudDownloadIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Functions")
external class FunctionsIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Settings")
external class SettingsIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Check")
external class CheckIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("CallReceived")
external class CallReceivedIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("GroupWork")
external class GroupWorkIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Visibility")
external class VisibilityIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Info")
external class InfoIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Block")
external class BlockIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("ViewList")
external class ViewListIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}


@JsName("Storage")
external class StorageIcon: Component<IconProps, react.State> {
    override fun render(): ReactElement<IconProps>?
}
