package tech.kzen.auto.client.wrap

import react.Component
import react.RBuilder
import react.RState
import react.ReactElement
import kotlin.reflect.KClass


fun iconClassForName(name: String): KClass<out Component<IconProps, RState>> {
    // TODO: is there a way to do this without manually writing these out?
    //  e.g. <Icon>edit_icon</Icon> in https://codesandbox.io/s/9yp4yk6qno

    return when (name) {
        "PlayArrow" ->
            PlayArrowIcon::class

        "OpenInNew" ->
            OpenInNewIcon::class

        "Http" ->
            HttpIcon::class

        "Keyboard" ->
            KeyboardIcon::class

        "DeviceHub" ->
            DeviceHubIcon::class

        "TouchApp" ->
            TouchAppIcon::class

        "Close" ->
            CloseIcon::class

        "Message" ->
            MessageIcon::class

        "Timer" ->
            TimerIcon::class

        "Send" ->
            SendIcon::class

        "Textsms" ->
            TextsmsIcon::class

        "Input" ->
            InputIcon::class

        "LooksOne" ->
            LooksOneIcon::class

        "PlaylistPlay" ->
            PlaylistPlayIcon::class

        "TableChart" ->
            TableChartIcon::class

        "Search" ->
            SearchIcon::class

        "CallSplit" ->
            CallSplitIcon::class

        "TripOrigin" ->
            TripOriginIcon::class

        "ExposurePlus1" ->
            ExposurePlus1Icon::class

        "ArrowDownward" ->
            ArrowDownwardIcon::class

        "SubdirectoryArrowLeft" ->
            SubdirectoryArrowLeftIcon::class

        "SubdirectoryArrowRight" ->
            SubdirectoryArrowRightIcon::class

        "ArrowForward" ->
            ArrowForwardIcon::class

        "ArrowBack" ->
            ArrowBackIcon::class

        "FilterList" ->
            FilterListIcon::class

        "Forward" ->
            ForwardIcon::class

        "FileCopy" ->
            FileCopyIcon::class

        "Add" ->
            AddIcon::class

        "CompareArrows" ->
            CompareArrowsIcon::class

        "PlaylistAdd" ->
            PlaylistAddIcon::class

        "Print" ->
            PrintIcon::class

        "ToggleOn" ->
            ToggleOnIcon::class

        "Flip" ->
            FlipIcon::class

        "AddBox" ->
            AddBoxIcon::class

        "Share" ->
            ShareIcon::class

        "Crop" ->
            CropIcon::class

        else ->
            TextureIcon::class
    }
}


fun RBuilder.iconByName(name: String): ReactElement? {
    return child(iconClassForName(name)) {}
}