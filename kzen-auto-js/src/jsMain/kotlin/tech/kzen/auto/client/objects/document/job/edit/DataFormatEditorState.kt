package tech.kzen.auto.client.objects.document.job.edit

import react.State
import tech.kzen.auto.common.data.format.FileFormatCatalog


external interface DataFormatEditorState: State {
    var catalog: FileFormatCatalog?
    var value: String?
    var creating: Boolean
    var createError: String?
}
