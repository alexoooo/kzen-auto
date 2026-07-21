package tech.kzen.auto.client.objects.document.script.model

import react.Component
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.model.document.DocumentPath


/**
 * The document's dependency analysis, served from [ScriptStore]'s memo so the four consumers in a
 * Script's subtree (branch gutters, dependency overlay, signature editor, move-to arrow) share one
 * computation per graph instead of re-lexing every value scalar once each per publish.
 *
 * Reached through the [DocumentBridge] (each consumer spends its single contextType slot on
 * DocumentBridgeContext), falling back to a direct analysis when the bridge is absent or the store
 * isn't provided yet — same result, just uncached.
 */
fun Component<*, *>.scriptDependencyAnalysis(
    clientState: ClientState,
    documentPath: DocumentPath
): ScriptDependencyAnalysis {
    val scriptStore = contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)

    return scriptStore?.dependencyAnalysis(clientState.graphDefinitionAttempt, documentPath)
        ?: ScriptDependencyAnalysis.analyze(clientState.graphDefinitionAttempt.successful(), documentPath)
}
