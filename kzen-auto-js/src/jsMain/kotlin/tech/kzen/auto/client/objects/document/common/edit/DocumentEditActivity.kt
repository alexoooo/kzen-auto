package tech.kzen.auto.client.objects.document.common.edit

import react.Component
import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.lib.common.model.document.DocumentPath


// The mounted document's handle onto LogicValidationGlobal's edit-pending channel, bound to its
// DocumentPath by ProjectController when the document's bridge is created. DebouncedSubmitter (the
// one place every debounced document edit flows through) marks/clears through this, so the run
// cluster's "revalidating" indicator lights on the first keystroke — no editor knows about
// LogicValidationGlobal and no props thread it.
class DocumentEditActivity(
    private val logicValidationGlobal: LogicValidationGlobal,
    private val documentPath: DocumentPath
) {
    fun mark(token: Any, pending: Boolean) {
        logicValidationGlobal.editActivity(documentPath, token, pending)
    }
}


// Owner-provided (no create()): ProjectController binds the instance per document.
object DocumentEditActivityKey: BridgeKey<DocumentEditActivity>


// Shared by every debounced editor, each of which spends its single contextType slot on
// DocumentBridgeContext (same shape as stepRowRefRegistry). Null where no bridge or document is
// bound; the submitter then skips marking.
fun Component<*, *>.documentEditActivity(): DocumentEditActivity? =
    contextValue<DocumentBridge?>()?.lookup(DocumentEditActivityKey)
