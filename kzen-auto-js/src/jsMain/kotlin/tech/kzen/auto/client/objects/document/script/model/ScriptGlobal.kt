package tech.kzen.auto.client.objects.document.script.model

import js.memory.WeakRef


// ScriptViewModeToggle is mounted in the header slot (a sibling React subtree to ScriptController's
// body), so React context can't reach it — it picks up the store via this weak global instead.
// Mirrors CustomGlobal. ScriptController.render() wires it during the render phase, before the
// toggle's componentDidMount reads it (see the NB in ScriptController).
object ScriptGlobal {
    private var ref: WeakRef<ScriptStore>? = null


    fun upsertWeak(store: ScriptStore) {
        ref = WeakRef(store)
    }


    fun get(): ScriptStore {
        val current = ref
            ?: error("ScriptGlobal never set")
        return current.deref()
            ?: error("ScriptGlobal store no longer available")
    }
}
