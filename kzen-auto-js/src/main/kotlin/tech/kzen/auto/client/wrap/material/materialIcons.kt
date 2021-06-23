@file:JsModule("@material-ui/icons")
package tech.kzen.auto.client.wrap.material


import react.Component
import react.RProps
import react.RState
import react.ReactElement
import kotlin.js.Json


// see: https://material-ui.com/style/icons/
// see: https://material.io/tools/icons/?style=baseline


// NB: can't create common MaterialIcon interface because 'external' doesn't support that

external interface IconProps: RProps {
    var title: String
//    var color: String
    var style: Json

    var onClick: () -> Unit
}


@JsName("Delete")
external class DeleteIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("PlayArrow")
external class PlayArrowIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Replay")
external class ReplayIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("KeyboardArrowUp")
external class KeyboardArrowUpIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("KeyboardArrowDown")
external class KeyboardArrowDownIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Save")
external class SaveIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("SaveAlt")
external class SaveAltIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Cancel")
external class CancelIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CancelPresentation")
external class CancelPresentationIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Close")
external class CloseIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}

@JsName("Clear")
external class ClearIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ArrowDownward")
external class ArrowDownwardIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("SubdirectoryArrowRight")
external class SubdirectoryArrowRightIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ArrowForward")
external class ArrowForwardIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ArrowForwardIos")
external class ArrowForwardIosIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ArrowBack")
external class ArrowBackIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("OpenInNew")
external class OpenInNewIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Http")
external class HttpIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Keyboard")
external class KeyboardIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("DeviceHub")
external class DeviceHubIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("TouchApp")
external class TouchAppIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Message")
external class MessageIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Texture")
external class TextureIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Timer")
external class TimerIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Edit")
external class EditIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Pause")
external class PauseIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Send")
external class SendIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Textsms")
external class TextsmsIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Input")
external class InputIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("LooksOne")
external class LooksOneIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("MoreVert")
external class MoreVertIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("MoreHoriz")
external class MoreHorizIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("SubdirectoryArrowLeft")
external class SubdirectoryArrowLeftIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Folder")
external class FolderIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("FolderOpen")
external class FolderOpenIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Receipt")
external class ReceiptIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("PlaylistPlay")
external class PlaylistPlayIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("FormatListNumbered")
external class FormatListNumberedIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("TableChart")
external class TableChartIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Search")
external class SearchIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CallSplit")
external class CallSplitIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("TripOrigin")
external class TripOriginIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ExposurePlus1")
external class ExposurePlus1Icon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("SettingsInputComponent")
external class SettingsInputComponentIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("FilterList")
external class FilterListIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Forward")
external class ForwardIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("FileCopy")
external class FileCopyIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Add")
external class AddIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("AddCircleOutline")
external class AddCircleOutlineIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Remove")
external class RemoveIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("RemoveCircle")
external class RemoveCircleIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("RemoveCircleOutline")
external class RemoveCircleOutlineIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CompareArrows")
external class CompareArrowsIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("PlaylistAdd")
external class PlaylistAddIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Print")
external class PrintIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Redo")
external class RedoIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Mail")
external class MailIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ToggleOn")
external class ToggleOnIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Flip")
external class FlipIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("AddBox")
external class AddBoxIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Share")
external class ShareIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Crop")
external class CropIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CameraAlt")
external class CameraAltIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Refresh")
external class RefreshIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("TransitEnterexit")
external class TransitEnterexitIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("KeyboardReturn")
external class KeyboardReturnIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ExpandLess")
external class ExpandLessIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ExpandMore")
external class ExpandMoreIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Scanner")
external class ScannerIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("MenuBook")
external class MenuBookIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Stop")
external class StopIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("PlayForWork")
external class PlayForWorkIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Error")
external class ErrorIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("ErrorOutline")
external class ErrorOutlineIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Pageview")
external class PageviewIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CloudDownload")
external class CloudDownloadIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Functions")
external class FunctionsIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Settings")
external class SettingsIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Check")
external class CheckIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("CallReceived")
external class CallReceivedIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("GroupWork")
external class GroupWorkIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Visibility")
external class VisibilityIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}

@JsName("Info")
external class InfoIcon: Component<IconProps, RState> {
    override fun render(): ReactElement?
}
