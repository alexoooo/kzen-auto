package tech.kzen.auto.client.wrap.iconify

import tech.kzen.auto.client.wrap.iconify.IconNames.legacyMaterialAlias


/**
 * Resolves an icon name to a fully-qualified Iconify name for `<Icon icon="...">`.
 *
 * Accepts three forms:
 *  - already-qualified `set:name` (e.g. `material-symbols:settings`) → passed through unchanged;
 *  - a legacy `@mui/icons-material` PascalCase name from notation saved against the previous icon
 *    registry → mapped to its material-symbols equivalent via [legacyMaterialAlias] (backward compat);
 *  - any other bare name → treated as a material-symbols name (PascalCase converted to kebab-case).
 *
 * First-party code and freshly-saved notation use `material-symbols:<name>` directly; the alias table
 * exists only so documents authored against the old `@mui/icons-material` registry keep rendering.
 */
object IconNames {
    const val defaultSet = "material-symbols"

    fun resolve(name: String): String =
        when {
            name.isEmpty() -> "$defaultSet:texture"
            ':' in name -> name
            else -> "$defaultSet:" + (legacyMaterialAlias[name] ?: kebabCase(name))
        }

    private fun kebabCase(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

    // Every icon name the previous @mui/icons-material registry accepted, mapped to its material-symbols
    // equivalent. A handful are intentionally not 1:1 where Material Symbols renamed or dropped the glyph
    // (e.g. CameraAlt→photo-camera, SaveAlt→download, ReportProblem→warning, PlusOne→exposure-plus-1).
    private val legacyMaterialAlias: Map<String, String> = mapOf(
        "Add" to "add",
        "AddBox" to "add-box",
        "AddCircleOutline" to "add-circle-outline",
        "ArrowBack" to "arrow-back",
        "ArrowDownward" to "arrow-downward",
        "ArrowForward" to "arrow-forward",
        "ArrowForwardIos" to "arrow-forward-ios",
        "CameraAlt" to "photo-camera",
        "Cancel" to "cancel",
        "CancelPresentation" to "cancel-presentation",
        "Check" to "check",
        "Close" to "close",
        "CloudDownload" to "cloud-download",
        "Code" to "code",
        "CompareArrows" to "compare-arrows",
        "Crop" to "crop",
        "Delete" to "delete",
        "DeviceHub" to "device-hub",
        "DragIndicator" to "drag-indicator",
        "Edit" to "edit",
        "ExpandLess" to "expand-less",
        "ExpandMore" to "expand-more",
        "FileCopy" to "file-copy",
        "FilterList" to "filter-list",
        "FolderOpen" to "folder-open",
        "Forward" to "forward",
        "Functions" to "functions",
        "GroupWork" to "group-work",
        "Http" to "http",
        "Input" to "input",
        "Keyboard" to "keyboard",
        "KeyboardArrowDown" to "keyboard-arrow-down",
        "KeyboardArrowUp" to "keyboard-arrow-up",
        "LooksOne" to "looks-one",
        "Mail" to "mail",
        "MenuBook" to "menu-book",
        "Message" to "chat",
        "MoreHoriz" to "more-horiz",
        "MoreVert" to "more-vert",
        "OpenInNew" to "open-in-new",
        "Pause" to "pause",
        "PlayArrow" to "play-arrow",
        "PlaylistAdd" to "playlist-add",
        "PlaylistPlay" to "playlist-play",
        "PlusOne" to "exposure-plus-1",
        "Print" to "print",
        "Public" to "public",
        "Redo" to "redo",
        "Refresh" to "refresh",
        "RemoveCircleOutline" to "do-not-disturb-on-outline",
        "Replay" to "replay",
        "Save" to "save",
        "SaveAlt" to "download",
        "Search" to "search",
        "Send" to "send",
        "Settings" to "settings",
        "Share" to "share",
        "Storage" to "storage",
        "Stop" to "stop",
        "SubdirectoryArrowLeft" to "subdirectory-arrow-left",
        "SubdirectoryArrowRight" to "subdirectory-arrow-right",
        "TableChart" to "table-chart",
        "Textsms" to "sms",
        "Timer" to "timer",
        "ToggleOn" to "toggle-on",
        "TouchApp" to "touch-app",
        "TripOrigin" to "trip-origin",
        "Visibility" to "visibility",
        "Texture" to "texture",
        "ReportProblem" to "warning",
        "SettingsInputComponent" to "settings-input-component",
    )
}
