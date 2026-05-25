package tech.kzen.auto.client.objects.document.script.model

import js.memory.WeakRef


// TODO: use React context instead?
object ScriptGlobal {
    private var scriptStore: WeakRef<ScriptStore>? = null


    fun upsertWeak(scriptStore: ScriptStore) {
        this.scriptStore = WeakRef(scriptStore)
//        if (this.scriptStore)
//
//        check(this.scriptStore == null) { "Already set" }
//        this.scriptStore = scriptStore
//        println("^^^^ set")
    }


    fun get(): ScriptStore {
        val ref = scriptStore
            ?: throw IllegalStateException("Never set")

        return ref.deref()
            ?: throw IllegalStateException("Not set anymore")
    }


//    fun clear() {
//        check(scriptStore != null) { "Already clear" }
//        scriptStore = null
//        println("^^^^ clear")
//    }
}