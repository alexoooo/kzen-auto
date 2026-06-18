package tech.kzen.auto.client.objects.document.script.model

import react.createContext
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptStepDragStore


// Scopes the shared drag store to the mounted ScriptController subtree, so every nested ScriptBranchDisplay
// reads the same instance without prop threading (mirrors ScriptStoreContext). See ScriptStepDragStore.
val ScriptStepDragStoreContext = createContext<ScriptStepDragStore?>(null)
