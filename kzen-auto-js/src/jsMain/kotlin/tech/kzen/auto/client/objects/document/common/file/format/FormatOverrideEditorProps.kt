package tech.kzen.auto.client.objects.document.common.file.format

import react.Props
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.lib.common.model.location.ObjectLocation


data class FormatOverrideEditorState(
    val source: ObjectLocation,
    val rowIndex: Int,
    val entry: FileSelectionEntry,
    val part: DataPart,
    val resolution: FormatResolutionDetail,
    val format: ConfiguredFormatDetail,
    val encodings: List<String>
)


external interface FormatOverrideEditorProps: Props {
    var editorState: FormatOverrideEditorState
    var onFormatChanged: (String) -> Unit
    var onEncodingChanged: (String) -> Unit
    var applying: Boolean
    var applyError: String?
    var onApply: (Map<String, String?>) -> Unit
}
