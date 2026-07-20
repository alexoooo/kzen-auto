package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.model.document.DocumentPath


/**
 * A run control (start / stop / step / …) the server refused, held until the next control action so the UI can
 * show it in context.
 *
 * [label] is what the user tried to do; [detail] is the server's reason (a compile failure names the offending
 * object). [documentPath] is the document the action was aimed at — a null path shows on any document, so a
 * failure is never hidden.
 */
data class ControlError(
    val label: String,
    val detail: String?,
    val documentPath: DocumentPath?
)
